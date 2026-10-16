package com.omepikya.commandcenter.execution;

import com.omepikya.commandcenter.core.CommandContext;
import com.omepikya.commandcenter.core.CommandResult;

/**
 * Unified, observable and cancellable execution lifecycle.
 *
 * Brain
 *   ↓
 * Coordinator
 *   ↓
 * Executor
 *   ↓
 * Recovery
 *   ↓
 * Result
 */
public final class ExecutionCoordinator {

    private final ActionExecutor executor;

    private final RecoveryEngine recoveryEngine;

    private final ExecutionEventBus eventBus;

    private final ExecutionStateStore stateStore;

    private final FailureClassifier failureClassifier;

    private volatile ExecutionTrace lastTrace;

    private volatile ExecutionResult lastResult;

    private volatile ExecutionCancellation activeCancellation;

    private volatile long executionTimeoutMs;

    public ExecutionCoordinator(
            ActionExecutor executor,
            RecoveryEngine recoveryEngine) {

        if (executor == null) {

            throw new IllegalArgumentException(
                    "ActionExecutor cannot be null");
        }

        if (recoveryEngine == null) {

            throw new IllegalArgumentException(
                    "RecoveryEngine cannot be null");
        }

        this.executor =
                executor;

        this.recoveryEngine =
                recoveryEngine;

        this.eventBus =
                new ExecutionEventBus();

        this.failureClassifier =
                new FailureClassifier();

        this.stateStore =
                executor.getContext() == null
                        ? null
                        : new ExecutionStateStore(
                                executor.getContext());

        this.executionTimeoutMs =
                0L;
    }

    public synchronized ExecutionResult execute(
            CommandContext context) {

        return execute(
                context,
                new ExecutionCancellation());
    }

    public synchronized ExecutionResult execute(
            CommandContext context,
            ExecutionCancellation cancellation) {

        ExecutionCancellation token =
                cancellation == null
                        ? new ExecutionCancellation()
                        : cancellation;

        String command =
                context == null
                        ? ""
                        : context.getRawCommand();

        ExecutionTrace trace =
                new ExecutionTrace(
                        command);

        lastTrace =
                trace;

        activeCancellation =
                token;

        publish(
                trace,
                "RECEIVED",
                command);

        persist(trace);

        try {

            // A token may already be cancelled before execution starts.
            // Return a terminal result instead of leaking the cancellation
            // exception before the executor's guarded section.
            if (token.isCancelled()) {
                return finishCancelled(
                        trace,
                        token.getReason());
            }

            if (context == null) {

                return finishFailure(
                        trace,
                        token,
                        "Execution context cannot be null.",
                        0,
                        false);
            }

            trace.transition(
                    ExecutionStatus.RUNNING);

            publish(
                    trace,
                    "RUNNING",
                    "Execution started.");

            persist(trace);

            if (isTimedOut(trace)) {

                return finishFailure(
                        trace,
                        token,
                        "Execution timeout reached before execution.",
                        0,
                        true);
            }

            publish(
                    trace,
                    "EXECUTION_START",
                    "Validation and action execution started.");

            ExecutionResult first;

            try {

                token.throwIfCancelled();

                first =
                        executor.execute(
                                context,
                                trace.getExecutionId());

            } catch (
                    ExecutionCancellation.ExecutionCancelledException e) {

                return finishCancelled(
                        trace,
                        e.getMessage());

            } catch (Exception e) {

                String message =
                        safeMessage(
                                e,
                                "Action execution failed.");

                first =
                        ExecutionResult.failure(
                                trace.getExecutionId(),
                                command,
                                message,
                                0,
                                CommandResult.failure(
                                        message));

                publish(
                        trace,
                        "EXECUTOR_EXCEPTION",
                        message);
            }

            if (first == null) {

                first =
                        ExecutionResult.failure(
                                trace.getExecutionId(),
                                command,
                                "No execution result returned.",
                                0,
                                CommandResult.failure(
                                        "No execution result returned."));
            }

            trace.setAttemptCount(
                    first.getAttemptCount());

            if (token.isCancelled()) {

                return finishCancelled(
                        trace,
                        token.getReason());
            }

            if (isTimedOut(trace)) {

                return finishFailure(
                        trace,
                        token,
                        "Execution timeout reached.",
                        first.getAttemptCount(),
                        true);
            }

            if (first.isSuccess()) {

                trace.addEvent(
                        "EXECUTION_VERIFIED");

                trace.transition(
                        ExecutionStatus.SUCCESS);

                lastResult =
                        first;

                publish(
                        trace,
                        "SUCCESS",
                        first.getMessage());

                persist(trace);

                return first;
            }

            trace.addEvent(
                    "EXECUTION_FAILED");

            FailureClassifier.Type type =
                    failureClassifier.classify(
                            first.getMessage());

            trace.setFailure(
                    new ExecutionFailure(
                            first.getExecutionId(),
                            first.getCommand(),
                            first.getMessage(),
                            null,
                            failureClassifier
                                    .isRetryable(type),
                            failureClassifier
                                    .requiresConfirmation(type),
                            first.getAttemptCount()));

            trace.transition(
                    ExecutionStatus.RECOVERING);

            publish(
                    trace,
                    "RECOVERY_START",
                    type.name());

            persist(trace);

            if (token.isCancelled()) {

                return finishCancelled(
                        trace,
                        token.getReason());
            }

            if (!failureClassifier
                    .isRetryable(type)

                    ||

                    failureClassifier
                            .requiresConfirmation(type)

                    ||

                    type ==
                            FailureClassifier.Type.CANCELLED

                    ||

                    type ==
                            FailureClassifier.Type.VALIDATION) {

                return finishFailure(
                        trace,
                        token,
                        first.getMessage(),
                        first.getAttemptCount(),
                        false,
                        first);
            }

            CommandResult recovered;

            try {

                recovered =
                        recoveryEngine.recover(
                                executor,
                                context,
                                first);

            } catch (Exception e) {

                publish(
                        trace,
                        "RECOVERY_EXCEPTION",
                        safeMessage(
                                e,
                                "Recovery failed."));

                recovered =
                        null;
            }

            if (token.isCancelled()) {

                return finishCancelled(
                        trace,
                        token.getReason());
            }

            if (recovered != null &&
                    recovered.isSuccess()) {

                ExecutionResult recoveredResult =
                        ExecutionResult.success(
                                first.getExecutionId(),
                                command,
                                recovered.getMessage(),
                                Math.max(
                                        1,
                                        first.getAttemptCount()
                                                + 1),
                                recovered);

                trace.setAttemptCount(
                        recoveredResult
                                .getAttemptCount());

                trace.addEvent(
                        "RECOVERY_VERIFIED");

                trace.transition(
                        ExecutionStatus.SUCCESS);

                lastResult =
                        recoveredResult;

                publish(
                        trace,
                        "RECOVERY_SUCCEEDED",
                        recovered.getMessage());

                persist(trace);

                return recoveredResult;
            }

            publish(
                    trace,
                    "RECOVERY_FAILED",
                    "Recovery did not produce a successful result.");

            return finishFailure(
                    trace,
                    token,
                    first.getMessage(),
                    first.getAttemptCount(),
                    false,
                    first);

        } finally {

            if (activeCancellation ==
                    token) {

                activeCancellation =
                        null;
            }
        }
    }

    /** Shared persistent execution history owned by the execution gateway. */
    public ExecutionHistory getExecutionHistory() {
        return executor.getHistory();
    }

    private ExecutionResult finishFailure(
            ExecutionTrace trace,
            ExecutionCancellation token,
            String message,
            int attempts,
            boolean timeout) {

        return finishFailure(
                trace,
                token,
                message,
                attempts,
                timeout,
                null);
    }

    private ExecutionResult finishFailure(
            ExecutionTrace trace,
            ExecutionCancellation token,
            String message,
            int attempts,
            boolean timeout,
            ExecutionResult source) {

        CommandResult result =
                source == null
                        ? null
                        : source.getCommandResult();

        if (result == null) {

            result =
                    CommandResult.failure(
                            message);
        }

        ExecutionResult failure =
                new ExecutionResult(
                        trace.getExecutionId(),
                        trace.getCommand(),
                        false,
                        message,
                        System.currentTimeMillis(),
                        attempts,
                        result,
                        ExecutionStatus.FAILED);

        trace.setAttemptCount(
                attempts);

        trace.addEvent(
                timeout
                        ? "TIMEOUT"
                        : "FAILED");

        trace.setFailure(
                new ExecutionFailure(
                        trace.getExecutionId(),
                        trace.getCommand(),
                        message,
                        null,
                        false,
                        false,
                        attempts));

        trace.transition(
                ExecutionStatus.FAILED);

        lastResult =
                failure;

        publish(
                trace,
                timeout
                        ? "TIMEOUT"
                        : "FAILED",
                message);

        persist(trace);

        return failure;
    }

    private ExecutionResult finishCancelled(
            ExecutionTrace trace,
            String reason) {

        String message =
                reason == null ||
                reason.trim().isEmpty()
                        ? "Execution cancelled."
                        : reason;

        ExecutionResult result =
                new ExecutionResult(
                        trace.getExecutionId(),
                        trace.getCommand(),
                        false,
                        message,
                        System.currentTimeMillis(),
                        trace.getAttemptCount(),
                        CommandResult.failure(
                                message),
                        ExecutionStatus.CANCELLED);

        trace.setFailure(
                new ExecutionFailure(
                        trace.getExecutionId(),
                        trace.getCommand(),
                        message,
                        null,
                        false,
                        false,
                        trace.getAttemptCount()));

        trace.addEvent(
                "CANCELLED");

        trace.transition(
                ExecutionStatus.CANCELLED);

        lastResult =
                result;

        publish(
                trace,
                "CANCELLED",
                message);

        persist(trace);

        return result;
    }

    private boolean isTimedOut(
            ExecutionTrace trace) {

        return executionTimeoutMs > 0L
                &&
                trace.getDurationMs() >=
                        executionTimeoutMs;
    }

    private void publish(
            ExecutionTrace trace,
            String type,
            String message) {

        eventBus.publish(
                trace.getExecutionId(),
                type,
                message);
    }

    private void persist(
            ExecutionTrace trace) {

        if (stateStore != null) {

            stateStore.save(trace);
        }
    }

    private String safeMessage(
            Exception e,
            String fallback) {

        return e != null &&
                e.getMessage() != null &&
                !e.getMessage()
                        .trim()
                        .isEmpty()

                ? e.getMessage()
                        .trim()

                : fallback;
    }

    public void cancelActive() {

        ExecutionCancellation token =
                activeCancellation;

        if (token != null) {

            token.cancel();
        }
    }

    public void cancelActive(
            String reason) {

        ExecutionCancellation token =
                activeCancellation;

        if (token != null) {

            token.cancel(reason);
        }
    }

    public boolean isExecutionActive() {

        return activeCancellation != null;
    }

    public ExecutionCancellation
    getActiveCancellation() {

        return activeCancellation;
    }

    public ExecutionTrace
    getLastTrace() {

        return lastTrace;
    }

    public ExecutionResult
    getLastResult() {

        return lastResult;
    }

    public ActionExecutor
    getExecutor() {

        return executor;
    }

    public RecoveryEngine
    getRecoveryEngine() {

        return recoveryEngine;
    }

    public ExecutionEventBus
    getEventBus() {

        return eventBus;
    }

    public ExecutionStateStore
    getStateStore() {

        return stateStore;
    }

    public FailureClassifier
    getFailureClassifier() {

        return failureClassifier;
    }

    public long
    getExecutionTimeoutMs() {

        return executionTimeoutMs;
    }

    public void setExecutionTimeoutMs(
            long value) {

        /*
         * 0 = disabled.
         *
         * Maximum:
         * 5 minutes.
         */
        executionTimeoutMs =
                Math.max(
                        0L,
                        Math.min(
                                300000L,
                                value));
    }
}