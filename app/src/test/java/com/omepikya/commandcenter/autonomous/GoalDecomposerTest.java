package com.omepikya.commandcenter.autonomous;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GoalDecomposerTest {

    @Test
    public void splitsMultiStepGoalInOrder() {
        GoalDecomposer decomposer = new GoalDecomposer();

        List<String> steps = decomposer.decompose(
                "Open settings and then open calculator");

        assertEquals(2, steps.size());
        assertEquals("Open settings", steps.get(0));
        assertEquals("open calculator", steps.get(1));
    }

    @Test
    public void rejectsGoalThatWouldBeSilentlyTruncated() {
        GoalDecomposer decomposer = new GoalDecomposer();
        decomposer.setMaxSteps(2);

        List<String> steps = decomposer.decompose(
                "first then second then third");

        assertTrue("A partial plan must never be executable", steps.isEmpty());
    }

    @Test
    public void preservesSingleStepGoalAtStepLimit() {
        GoalDecomposer decomposer = new GoalDecomposer();
        decomposer.setMaxSteps(1);

        List<String> steps = decomposer.decompose("Open calculator");

        assertEquals(1, steps.size());
        assertEquals("Open calculator", steps.get(0));
    }

    @Test
    public void rejectsEmptyGoal() {
        assertTrue(new GoalDecomposer().decompose("  ").isEmpty());
    }
}
