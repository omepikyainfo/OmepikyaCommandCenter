package com.omepikya.commandcenter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small deterministic helpers for commands that must work without network access. */
public final class OfflineCommandRouter {
    private static final Pattern ARITHMETIC = Pattern
            .compile("^(-?\\d+(?:\\.\\d+)?)([+*/-])(-?\\d+(?:\\.\\d+)?)$");
    private static final Pattern TIME_WORD = Pattern.compile("\\b(time|clock)\\b");
    private static final Pattern DATE_WORD = Pattern.compile("\\b(date|today)\\b");

    private OfflineCommandRouter() { }

    /** App-private persistence supplied by the Android layer; tests can use an in-memory store. */
    public interface NoteStore {
        String read();
        void save(String note);
        void clear();
    }

    /** Accepts one binary operation only. It never evaluates user-provided code. */
    public static String tryCalculate(String message) {
        if (message == null) return null;
        String expression = message.toLowerCase(Locale.ROOT)
                .replace("calculate", "")
                .replace("what is", "")
                .replace("how much is", "")
                .replace("divided by", "/")
                .replace("multiplied by", "*")
                .replace("plus", "+")
                .replace("minus", "-")
                .replace("times", "*")
                .replace(" ", "")
                .replace("?", "");
        Matcher matcher = ARITHMETIC.matcher(expression);
        if (!matcher.matches()) return null;

        final double left;
        final double right;
        try {
            left = Double.parseDouble(matcher.group(1));
            right = Double.parseDouble(matcher.group(3));
        } catch (NumberFormatException ignored) {
            return "I couldn't read those numbers.";
        }

        final double result;
        switch (matcher.group(2)) {
            case "+": result = left + right; break;
            case "-": result = left - right; break;
            case "*": result = left * right; break;
            case "/":
                if (right == 0.0) return "Division by zero is undefined.";
                result = left / right;
                break;
            default: return null;
        }
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            return "That calculation is outside the supported range.";
        }
        return "The answer is " + new DecimalFormat("0.##########", DecimalFormatSymbols.getInstance(Locale.US)).format(result) + ".";
    }

    /** Avoid treating unrelated words such as "sometimes" as time questions. */
    public static boolean isTimeQuestion(String message) {
        if (message == null) return false;
        String lower = message.trim().toLowerCase(Locale.ROOT);
        return TIME_WORD.matcher(lower).find()
                || lower.contains("what time is it")
                || lower.contains("current time")
                || lower.contains("tell me the time");
    }

    /** Recognize date requests without matching unrelated words such as "database". */
    public static boolean isDateQuestion(String message) {
        if (message == null) return false;
        String lower = message.trim().toLowerCase(Locale.ROOT);
        return DATE_WORD.matcher(lower).find()
                || lower.contains("what day is it")
                || lower.contains("which day is it");
    }

    public static String timeReply(Date now) {
        return "The current device time is " + DateFormat.getTimeInstance().format(now) + ".";
    }

    public static String dateReply(Date now) {
        return "Today's date on this device is " + DateFormat.getDateInstance().format(now) + ".";
    }

    /**
     * Handles the existing single-note commands without Android dependencies.
     * Returns null when the message is not a note command.
     */
    public static String handleNoteCommand(String message, NoteStore store) {
        if (message == null || store == null) return null;
        String trimmed = message.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (lower.startsWith("remember that ")) {
            String note = trimmed.substring("remember that ".length()).trim();
            if (note.isEmpty()) return null;
            store.save(note);
            return "Saved privately on this phone: " + note;
        }
        if (lower.contains("what do you remember")
                || lower.contains("what did i ask you to remember")) {
            String note = store.read();
            return note == null || note.trim().isEmpty()
                    ? "I don't have a saved note yet. Say: remember that [your note]."
                    : "Your saved note on this phone is: " + note;
        }
        if (lower.contains("forget what i asked you to remember") || lower.equals("forget that")) {
            store.clear();
            return "I've cleared the saved Tiny note from this phone.";
        }
        return null;
    }


    /**
     * Handles simple personal facts such as "my name is Vivek" and "what is my name".
     * The Android layer supplies a private persistent store; no network is used.
     */
    public static String handlePersonalMemoryCommand(String message, NoteStore store) {
        if (message == null || store == null) return null;
        String trimmed = message.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (lower.equals("my name is") || lower.equals("call me") || lower.equals("remember my name is")) {
            return "Tell me the name you\'d like me to remember.";
        }
        String[] savePrefixes = {
                "remember my name is ", "my name is ", "call me "
        };
        for (String prefix : savePrefixes) {
            if (lower.startsWith(prefix)) {
                String name = trimmed.substring(prefix.length()).trim();
                String nameLower = name.toLowerCase(Locale.ROOT);
                if (nameLower.endsWith(", save it")) name = name.substring(0, name.length() - ", save it".length()).trim();
                else if (nameLower.endsWith(" save it")) name = name.substring(0, name.length() - " save it".length()).trim();
                if (name.endsWith(".")) name = name.substring(0, name.length() - 1).trim();
                if (name.isEmpty()) return "Tell me the name you\'d like me to remember.";
                if (name.length() > 80) return "That name is too long to save. Please keep it under 80 characters.";
                store.save("name:" + name);
                return "I'll remember your name is " + name + ". Saved privately on this phone.";
            }
        }

        if (lower.matches("^(what is|what's|tell me) my name[?!. ]*$")
                || lower.matches("^do you know my name[?!. ]*$")) {
            String saved = store.read();
            if (saved != null && saved.startsWith("name:") && saved.length() > 5) {
                return "Your name is " + saved.substring(5) + ".";
            }
            return "I don't know your name yet. Say: My name is Vivek, save it.";
        }
        return null;
    }

    /** Small, honest identity/help responses; null means this helper does not handle the prompt. */
    public static String helpReply(String message) {
        if (message == null) return null;
        String lower = message.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("who are you") || lower.equals("what are you")) {
            return "I'm Tiny, Omepikya's phone-first assistant. I can handle a few built-in commands offline; open-ended local AI needs an on-device model that isn't installed yet.";
        }
        if (lower.contains("help") || lower.contains("what can you do")) {
            return "Offline Tiny can handle greetings, date/time questions, simple arithmetic, and one private saved note. Broader conversation needs a compatible on-device language model.";
        }
        if (lower.contains("offline") || lower.contains("internet") || lower.contains("render")) {
            return "Tiny's basic commands run on this phone without Render or internet. Rich, open-ended AI still needs an on-device language model.";
        }
        return null;
    }
}
