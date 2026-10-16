package com.omepikya.commandcenter.nlp;

import com.omepikya.commandcenter.core.CommandType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IntentParserTest {

    private final IntentParser parser = new IntentParser();

    @Test
    public void parsesOpenApp() {
        Intent intent = parser.parse("Open YouTube");
        assertEquals(CommandType.OPEN_APP, intent.getCommandType());
        assertEquals("YouTube", intent.getEntity("app"));
        assertTrue(intent.getConfidence() >= 0.70);
    }

    @Test
    public void parsesSystemSettings() {
        Intent intent = parser.parse("Open Wi-Fi settings");
        assertEquals(CommandType.SYSTEM_SETTING, intent.getCommandType());
        assertTrue(intent.isConfident());
    }

    @Test
    public void rejectsEmptyCommand() {
        Intent intent = parser.parse("   ");
        assertEquals(CommandType.UNKNOWN, intent.getCommandType());
        assertEquals(0.0, intent.getConfidence(), 0.0);
    }

    @Test
    public void safelyRejectsNullCommand() {
        Intent intent = parser.parse(null);
        assertNotNull(intent);
        assertEquals(CommandType.UNKNOWN, intent.getCommandType());
        assertEquals(0.0, intent.getConfidence(), 0.0);
    }

    @Test
    public void normalizesRepeatedWhitespaceBeforeMatching() {
        Intent intent = parser.parse("  Open   YouTube  ");
        assertEquals(CommandType.OPEN_APP, intent.getCommandType());
        assertEquals("Open YouTube", intent.getRawCommand());
        assertEquals("YouTube", intent.getEntity("app"));
    }

    @Test
    public void parsesWhatsappMessage() {
        Intent intent = parser.parse("Send a WhatsApp message to Rahul saying hello");
        assertEquals(CommandType.COMMUNICATION, intent.getCommandType());
        assertEquals("Rahul", intent.getEntity("person"));
        assertEquals("hello", intent.getEntity("message"));
    }
}
