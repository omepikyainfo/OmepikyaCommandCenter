package com.omepikya.commandcenter;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small deterministic helpers for commands that must work without network access. */
public final class OfflineCommandRouter {
    private static final Pattern ARITHMETIC = Pattern
            .compile("^(-?\\d+(?:\\.\\d+)?)([+*/-])(-?\\d+(?:\\.\\d+)?)$");

    private OfflineCommandRouter() { }

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
        return "The answer is " + new DecimalFormat("0.##########").format(result) + ".";
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
