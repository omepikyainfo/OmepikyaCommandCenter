package com.omepikya.commandcenter.autonomous;

import com.omepikya.commandcenter.core.CommandResult;
import com.omepikya.commandcenter.execution.ExecutionFailure;
import com.omepikya.commandcenter.execution.ExecutionStatus;
import com.omepikya.commandcenter.execution.ExecutionTrace;

/**
 * Executes an autonomous plan one step at a time.
 *
 * Every step is delegated to CommandBrain through
 * CommandHandler.
 *
 * Therefore autonomous execution cannot bypass:
 *
 * NLP
 * context
 * planning
 * safety
 * validation
 * execution
 * recovery
 */
public final class AutonomousExecutor {

    public interface CommandHandler {

        CommandResult execute(
                String command);
    }

    private final PlanVerifier verifier;

    private final SafetyEscalator safety;

    private int maxAttemptsPerStep = 1;

    private ExecutionTrace lastTrace;

    public AutonomousExecutor(
            PlanVerifier verifier,
            SafetyEscalator safety) {

        this.verifier =
                verifier == null
                        ? new PlanVerifier()
                        : verifier;

        this.safety =
                safety == null
                        ? new SafetyEscalator()
                        : safety;
    }

    public synchronized CommandResult execute(
            AutonomousPlan plan,
            CommandHandler handler,
            AutonomousPersistence persistence) {

        if (!verifier.validate(
                plan)) {

            return CommandResult.failure(
                    "Autonomous plan is invalid.");
        }

        if (handler == null) {

            return CommandResult.failure(
                    "Autonomous command handler is unavailable.");
        }

        String goalText =
                plan.getGoal() == null
                        ? "autonomous-goal"
                        : plan.getGoal()
                                .getText();

        lastTrace =
                new ExecutionTrace(
                        goalText);

        lastTrace.transition(
                ExecutionStatus.RUNNING);

        lastTrace.addEvent(
                "AUTONOMOUS_PLAN_STARTED");

        plan.getGoal().setStatus(
                AutonomousGoal.RUNNING);

        save(
                persistence,
                plan);

        StringBuilder messages =
                new StringBuilder();

        while (plan.hasNext()) {

            AutonomousStep step =
                    plan.current();

            if (step == null ||
                    !step.isValid()) {

                plan.getGoal().setStatus(
                        AutonomousGoal.FAILED);

                save(
                        persistence,
                        plan);

                lastTrace.setFailure(
                        new ExecutionFailure(
                                lastTrace
                                        .getExecutionId(),
                                goalText,
                                "Autonomous execution encountered an invalid step.",
                                null,
                                false,
                                false,
                                0));

                lastTrace.addEvent(
                        "INVALID_STEP");

                lastTrace.transition(
                        ExecutionStatus.FAILED);

                return safety.stop(
                        "Autonomous execution encountered an invalid step.");
            }

            String command =
                    step.getCommand();

            lastTrace.addEvent(
                    "STEP_START: " +
                            command);

            CommandResult result =
                    null;

            for (int attempt = 0;
                    attempt <
                            maxAttemptsPerStep;
                    attempt++) {

                step.incrementAttempts();

                lastTrace.setAttemptCount(
                        step.getAttempts());

                try {

                    /*
                     * Critical rule:
                     * the handler normally points back
                     * to CommandBrain.process().
                     */
                    result =
                            handler.execute(
                                    command);

                } catch (Exception e) {

                    result =
                            CommandResult.failure(
                                    safeMessage(
                                            e,
                                            "Execution exception."));

                    lastTrace.addEvent(
                            "STEP_EXCEPTION");
                }

                if (result != null &&
                        result.isSuccess()) {

                    break;
                }
            }

            if (result == null) {

                result =
                        CommandResult.failure(
                                "No result returned for: " +
                                        command);
            }

            step.setResult(
                    result.getMessage());

            if (!result.isSuccess()) {

                step.setFailed(
                        true);

                lastTrace.addEvent(
                        "STEP_FAILED: " +
                                command);

                boolean escalation =
                        safety.requiresEscalation(
                                result);

                lastTrace.setFailure(
                        new ExecutionFailure(
                                lastTrace
                                        .getExecutionId(),
                                goalText,
                                result.getMessage(),
                                command,
                                false,
                                escalation,
                                step.getAttempts()));

                if (escalation) {

                    plan.getGoal().setStatus(
                            AutonomousGoal
                                    .WAITING_CONFIRMATION);

                    lastTrace.transition(
                            ExecutionStatus.CANCELLED);

                } else {

                    plan.getGoal().setStatus(
                            AutonomousGoal.FAILED);

                    lastTrace.transition(
                            ExecutionStatus.FAILED);
                }

                save(
                        persistence,
                        plan);

                return result;
            }

            /*
             * Explicitly verify and mark the step complete.
             */
            verifier.verifyStep(
                    step,
                    true,
                    result.getMessage());

            step.setCompleted(
                    true);

            lastTrace.addEvent(
                    "STEP_VERIFIED: " +
                            command);

            if (result.getMessage() != null &&
                    !result.getMessage()
                            .trim()
                            .isEmpty()) {

                if (messages.length() > 0) {

                    messages.append(
                            '\n');
                }

                messages.append(
                        result.getMessage()
                                .trim());
            }

            plan.advance();

            save(
                    persistence,
                    plan);
        }

        if (verifier.verifyComplete(
                plan)) {

            plan.getGoal().setStatus(
                    AutonomousGoal.COMPLETED);

            save(
                    persistence,
                    plan);

            lastTrace.addEvent(
                    "AUTONOMOUS_PLAN_VERIFIED");

            lastTrace.transition(
                    ExecutionStatus.SUCCESS);

            return CommandResult.success(
                    messages.length() == 0
                            ? "Autonomous goal completed."
                            : messages.toString());
        }

        plan.getGoal().setStatus(
                AutonomousGoal.FAILED);

        save(
                persistence,
                plan);

        lastTrace.addEvent(
                "AUTONOMOUS_PLAN_VERIFICATION_FAILED");

        lastTrace.transition(
                ExecutionStatus.FAILED);

        return CommandResult.failure(
                "Autonomous goal did not complete.");
    }

    private void save(
            AutonomousPersistence persistence,
            AutonomousPlan plan) {

        if (persistence != null) {

            persistence.save(
                    plan);
        }
    }

    private String safeMessage(
            Exception e,
            String fallback) {

        if (e != null &&
                e.getMessage() != null &&
                !e.getMessage()
                        .trim()
                        .isEmpty()) {

            return e.getMessage()
                    .trim();
        }

        return fallback;
    }

    public int
    getMaxAttemptsPerStep() {

        return maxAttemptsPerStep;
    }

    public void
    setMaxAttemptsPerStep(
            int value) {

        maxAttemptsPerStep =
                Math.max(
                        1,
                        Math.min(
                                2,
                                value));
    }

    public synchronized ExecutionTrace
    getLastTrace() {

        return lastTrace;
    }
}