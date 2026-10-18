package com.omepikya.commandcenter;

import org.junit.Test;
import static org.junit.Assert.*;

public class OfflineCommandRouterTest {
    @Test public void calculatesBasicArithmetic() {
        assertEquals("The answer is 5.", OfflineCommandRouter.tryCalculate("what is 2 + 3"));
        assertEquals("The answer is 12.", OfflineCommandRouter.tryCalculate("3 times 4"));
        assertEquals("The answer is 2.5.", OfflineCommandRouter.tryCalculate("5 divided by 2"));
    }

    @Test public void rejectsDivisionByZero() {
        assertEquals("Division by zero is undefined.", OfflineCommandRouter.tryCalculate("9 / 0"));
    }

    @Test public void refusesMultiOperationExpressionsAndCode() {
        assertNull(OfflineCommandRouter.tryCalculate("2 + 3 * 4"));
        assertNull(OfflineCommandRouter.tryCalculate("System.exit(0)"));
        assertNull(OfflineCommandRouter.tryCalculate(null));
    }

    @Test public void providesHonestOfflineIdentityAndHelp() {
        assertTrue(OfflineCommandRouter.helpReply("who are you").contains("I'm Tiny"));
        assertTrue(OfflineCommandRouter.helpReply("what can you do").contains("on-device language model"));
        assertNull(OfflineCommandRouter.helpReply("tell me a story"));
    }
}
