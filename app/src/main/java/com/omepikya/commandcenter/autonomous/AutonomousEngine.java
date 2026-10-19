package com.omepikya.commandcenter.autonomous;

import android.content.Context;

import com.omepikya.commandcenter.core.CommandResult;
import com.omepikya.commandcenter.execution.ExecutionTrace;

/**
 * Autonomous command engine.
 *
 * Responsible for:
 *
 * Goal
 *  ↓
 * Planning
 *  ↓
 * Execution
 *  ↓
 * Verification
 *  ↓
 * Replanning
 *  ↓
 * Persistence
 */
public final class AutonomousEngine {

    private final AutonomousPlanner planner;

    private final PlanVerifier verifier;

    private final Replanner replanner;

    private final SafetyEscalator safety;

    private final AutonomousExecutor executor;

    private final AutonomousPersistence persistence;

    private int maxReplans = 2;

    public AutonomousEngine(
            Context context) {

        if (context == null) {

            throw new IllegalArgumentException(
                    "Context cannot be null");
        }

        planner =
                new AutonomousPlanner();

        verifier =
                new PlanVerifier();

        replanner =
                new Replanner();

        safety =
                new SafetyEscalator();

        executor =
                new AutonomousExecutor(
                        verifier,
                        safety);

        persistence =
                new AutonomousPersistence(
                        context);
    }

    public synchronized CommandResult
    executeGoal(
            String goal,
            AutonomousExecutor.CommandHandler
                    handler) {

        if (goal == null ||
                goal.trim().isEmpty()) {

            return CommandResult.failure(
                    "Autonomous goal cannot be empty.");
        }

        if (handler == null) {

            return CommandResult.failure(
                    "Autonomous command handler is unavailable.");
        }

        AutonomousPlan plan =
                planner.create(
                        goal);

        if (!verifier.validate(
                plan)) {

            return CommandResult.failure(
                    "I could not create a valid autonomous plan.");
        }

        int replans = 0;

        while (true) {

            CommandResult result =
                    executor.execute(
                            plan,
                            handler,
                            persistence);

            if (result != null &&
                    result.isSuccess()) {

                return result;
            }

            if (result == null) {

                return safety.stop(
                        "Autonomous execution failed.");
            }

            /*
             * Never automatically replan through a
             * safety escalation.
             */
            if (safety.requiresEscalation(
                    result)) {

                return result;
            }

            /*
             * Replanning currently copies the failed step into the
             * replacement plan. Until handlers can explicitly classify
             * a failure as safe to retry, stop instead of repeating a
             * command that may already have produced a side effect.
             */
            ExecutionTrace trace = executor.getLastTrace();
            if (trace == null ||
                    trace.getFailure() == null ||
                    !trace.getFailure().isRetryable()) {
                return result;
            }

            if (!replanner.canReplan(
                    replans) ||
                    replans >= maxReplans) {

                return result;
            }

            AutonomousPlan next =
                    replanner.replan(
                            plan,
                            result.getMessage());

            if (next == null ||
                    !verifier.validate(
                            next)) {

                return result;
            }

            plan =
                    next;

            replans++;
        }
    }

    public synchronized CommandResult
    resume(
            AutonomousExecutor.CommandHandler
                    handler) {

        if (handler == null) {

            return CommandResult.failure(
                    "Autonomous command handler is unavailable.");
        }

        AutonomousPlan plan =
                persistence.load();

        if (plan == null) {

            return CommandResult.failure(
                    "There is no saved autonomous task.");
        }

        if (plan.isComplete()) {

            return CommandResult.success(
                    "The saved autonomous task is already complete.");
        }

        if (plan.isCancelled()) {

            return CommandResult.failure(
                    "The saved autonomous task was cancelled.");
        }

        String savedStatus = plan.getGoal().getStatus();

        // A failed step may have performed a side effect before its handler
        // returned an error. Do not repeat it blindly after process restart.
        if (AutonomousGoal.FAILED.equals(savedStatus)) {
            return CommandResult.failure(
                    "The saved task failed at a step. It was not resumed automatically to avoid repeating an action that may already have taken effect.");
        }

        if (AutonomousGoal.WAITING_CONFIRMATION.equals(savedStatus)) {
            return CommandResult.failure(
                    "The saved task is waiting for confirmation and cannot be resumed automatically.");
        }

        return executor.execute(
                plan,
                handler,
                persistence);
    }

    public synchronized void cancel() {

        AutonomousPlan plan =
                persistence.load();

        if (plan != null) {

            plan.cancel();

            persistence.save(
                    plan);
        }
    }

    public synchronized void
    clearSavedTask() {

        persistence.clear();
    }

    public boolean hasSavedTask() {

        return persistence.hasSavedPlan();
    }

    public String getLastGoal() {

        return persistence.getGoal();
    }

    public int getLastCursor() {

        return persistence.getCursor();
    }

    public String getLastStatus() {

        return persistence.getStatus();
    }

    public AutonomousPlanner
    getPlanner() {

        return planner;
    }

    public AutonomousExecutor
    getExecutor() {

        return executor;
    }

    public Replanner
    getReplanner() {

        return replanner;
    }

    /**
     * Returns the latest autonomous execution trace.
     */
    public ExecutionTrace
    getLastTrace() {

        return executor.getLastTrace();
    }

    public AutonomousPersistence
    getPersistence() {

        return persistence;
    }

    public int getMaxReplans() {

        return maxReplans;
    }

    public void setMaxReplans(
            int value) {

        maxReplans =
                Math.max(
                        0,
                        Math.min(
                                3,
                                value));
    }
}