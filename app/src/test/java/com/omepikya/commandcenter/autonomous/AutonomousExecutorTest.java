package com.omepikya.commandcenter.autonomous;

import com.omepikya.commandcenter.core.CommandResult;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutonomousExecutorTest {

    @Test
    public void executesEveryStepAndCompletesGoal() {
        AutonomousPlan plan =
                new AutonomousPlan(
                        new AutonomousGoal("goal-1", "run routine"));
        plan.addStep("open calculator");
        plan.addStep("open settings");

        AtomicInteger calls =
                new AtomicInteger();

        AutonomousExecutor executor =
                new AutonomousExecutor(
                        new PlanVerifier(),
                        new SafetyEscalator());

        CommandResult result =
                executor.execute(
                        plan,
                        command -> {
                            calls.incrementAndGet();
                            return CommandResult.success(
                                    "done: " + command);
                        },
                        null);

        assertTrue(result.isSuccess());
        assertEquals(2, calls.get());
        assertTrue(plan.isComplete());
        assertEquals(
                AutonomousGoal.COMPLETED,
                plan.getGoal().getStatus());
        assertEquals(100.0, plan.progress(), 0.001);
    }

    @Test
    public void stopsOnFailedStepAndPreservesProgress() {
        AutonomousPlan plan =
                new AutonomousPlan(
                        new AutonomousGoal("goal-2", "run routine"));
        plan.addStep("first");
        plan.addStep("second");

        AtomicInteger calls =
                new AtomicInteger();

        AutonomousExecutor executor =
                new AutonomousExecutor(
                        new PlanVerifier(),
                        new SafetyEscalator());

        CommandResult result =
                executor.execute(
                        plan,
                        command -> {
                            calls.incrementAndGet();
                            return "first".equals(command)
                                    ? CommandResult.success("first ok")
                                    : CommandResult.failure("temporary failure");
                        },
                        null);

        assertFalse(result.isSuccess());
        assertEquals(2, calls.get());
        assertEquals(1, plan.getCursor());
        assertEquals(50.0, plan.progress(), 0.001);
        assertEquals(
                AutonomousGoal.FAILED,
                plan.getGoal().getStatus());
        assertFalse(
                executor.getLastTrace()
                        .getFailure()
                        .isRetryable());
    }

    @Test
    public void capsAttemptsPerStep() {
        AutonomousPlan plan =
                new AutonomousPlan(
                        new AutonomousGoal("goal-3", "retry"));
        plan.addStep("unstable");

        AtomicInteger calls =
                new AtomicInteger();

        AutonomousExecutor executor =
                new AutonomousExecutor(
                        new PlanVerifier(),
                        new SafetyEscalator());
        executor.setMaxAttemptsPerStep(2);

        CommandResult result =
                executor.execute(
                        plan,
                        command -> {
                            calls.incrementAndGet();
                            return CommandResult.failure("temporary failure");
                        },
                        null);

        assertFalse(result.isSuccess());
        assertEquals(2, calls.get());
        assertEquals(
                2,
                plan.current().getAttempts());
    }
}
