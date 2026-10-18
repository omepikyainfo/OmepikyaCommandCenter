package com.omepikya.commandcenter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OfflineNoteCommandRouterTest {
    @Test
    public void savesRecallsAndDeletesPrivateNote() {
        MemoryNoteStore store = new MemoryNoteStore();

        assertEquals("Saved privately on this phone: I prefer English",
                OfflineCommandRouter.handleNoteCommand("Remember that I prefer English", store));
        assertEquals("I prefer English", store.read());
        assertEquals("Your saved note on this phone is: I prefer English",
                OfflineCommandRouter.handleNoteCommand("what do you remember?", store));

        assertEquals("I've cleared the saved Tiny note from this phone.",
                OfflineCommandRouter.handleNoteCommand("forget that", store));
        assertNull(store.read());
        assertEquals("I don't have a saved note yet. Say: remember that [your note].",
                OfflineCommandRouter.handleNoteCommand("what did I ask you to remember?", store));
    }

    @Test
    public void ignoresEmptyAndUnrelatedNoteCommands() {
        MemoryNoteStore store = new MemoryNoteStore();
        store.save("keep this");

        assertNull(OfflineCommandRouter.handleNoteCommand("remember that   ", store));
        assertNull(OfflineCommandRouter.handleNoteCommand("tell me a story", store));
        assertNull(OfflineCommandRouter.handleNoteCommand(null, store));
        assertNull(OfflineCommandRouter.handleNoteCommand("forget that", null));
        assertEquals("keep this", store.read());
    }

    private static final class MemoryNoteStore implements OfflineCommandRouter.NoteStore {
        private String note;

        @Override public String read() {
            return note;
        }

        @Override public void save(String value) {
            note = value;
        }

        @Override public void clear() {
            note = null;
        }
    }
}
