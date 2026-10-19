package com.omepikya.commandcenter.autonomous;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlanVerifierTest {

    @Test
    public void acceptsIntentionalRepeatedCommands() {
        AutonomousPlan plan = new AutonomousPlan(
                new AutonomousGoal("test", "Increase volume twice"));
        plan.addStep("volume up");
        plan.addStep("volume up");

        assertTrue(new PlanVerifier().validate(plan));
    }

    @Test
    public void rejectsEmptyPlan() {
        AutonomousPlan plan = new AutonomousPlan(
                new AutonomousGoal("test", "Do something"));

        assertFalse(new PlanVerifier().validate(plan));
    }

    @Test
    public void rejectsPlanWithoutGoal() {
        assertFalse(new PlanVerifier().validate(null));
    }
}
