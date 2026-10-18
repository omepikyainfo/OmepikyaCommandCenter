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
    @Test public void recognizesTimeAndDateQuestionsWithoutBroadSubstringMatches() {
        assertTrue(OfflineCommandRouter.isTimeQuestion("what time is it"));
        assertTrue(OfflineCommandRouter.isTimeQuestion("tell me the time"));
        assertFalse(OfflineCommandRouter.isTimeQuestion("sometimes I forget"));
        assertTrue(OfflineCommandRouter.isDateQuestion("what is today's date"));
        assertTrue(OfflineCommandRouter.isDateQuestion("what day is it today"));
        assertFalse(OfflineCommandRouter.isDateQuestion("update my database"));
    }

    @Test public void formatsDeviceDateAndTimeReplies() {
        java.util.Date fixed = new java.util.Date(0L);
        assertTrue(OfflineCommandRouter.timeReply(fixed).startsWith("The current device time is "));
        assertTrue(OfflineCommandRouter.dateReply(fixed).startsWith("Today's date on this device is "));
    }

}
