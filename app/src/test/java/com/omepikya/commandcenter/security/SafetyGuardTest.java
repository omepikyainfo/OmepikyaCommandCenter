package com.omepikya.commandcenter.security;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SafetyGuardTest {

    private final SafetyGuard guard =
            new SafetyGuard();

    @Test
    public void ordinaryCommandCanExecuteWithoutConfirmation() {
        assertTrue(
                guard.canExecute(
                        "open calculator",
                        false));
    }

    @Test
    public void confirmationProtectedCommandCannotBypassConfirmation() {
        String command = "delete all files";
        SafetyDecision decision = guard.check(command);

        assertTrue(decision.isAllowed());
        assertTrue(decision.requiresConfirmation());
        assertFalse(guard.canExecute(command, false));
        assertTrue(guard.canExecute(command, true));
    }

    @Test
    public void emptyCommandIsRejected() {
        assertFalse(guard.canExecute("   ", false));
    }
}
