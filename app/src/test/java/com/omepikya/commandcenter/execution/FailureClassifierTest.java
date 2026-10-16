package com.omepikya.commandcenter.execution;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FailureClassifierTest {

    private final FailureClassifier classifier =
            new FailureClassifier();

    @Test
    public void safetyFailureNeverRetries() {
        FailureClassifier.Analysis a =
                classifier.analyze("Blocked by safety policy");

        assertEquals(
                FailureClassifier.Type.SAFETY,
                a.getType());
        assertFalse(a.isRetryable());
        assertTrue(a.requiresConfirmation());
    }

    @Test
    public void networkFailureCanRetry() {
        FailureClassifier.Analysis a =
                classifier.analyze("Network connection failed");

        assertEquals(
                FailureClassifier.Type.NETWORK,
                a.getType());
        assertTrue(a.isRetryable());
        assertFalse(a.requiresConfirmation());
    }

    @Test
    public void timeoutCanRetryAndReplan() {
        FailureClassifier.Analysis a =
                classifier.analyze("Execution timed out");

        assertEquals(
                FailureClassifier.Type.TIMEOUT,
                a.getType());
        assertTrue(a.isRetryable());
        assertTrue(a.requiresReplan());
    }

    @Test
    public void temporarilyUnavailableFailureCanRetry() {
        FailureClassifier.Analysis a =
                classifier.analyze("Service temporarily unavailable");

        assertEquals(
                FailureClassifier.Type.TEMPORARY,
                a.getType());
        assertTrue(a.isRetryable());
        assertFalse(a.requiresConfirmation());
    }

    @Test
    public void cancellationNeverRetries() {
        FailureClassifier.Analysis a =
                classifier.analyze("Execution cancelled");

        assertEquals(
                FailureClassifier.Type.CANCELLED,
                a.getType());
        assertFalse(a.isRetryable());
    }
}
