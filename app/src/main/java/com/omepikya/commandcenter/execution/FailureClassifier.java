package com.omepikya.commandcenter.execution;

import java.util.Locale;

/**
 * ============================================================
 * OMEPIKYA COMMAND CENTER
 * FAILURE CLASSIFIER
 * ============================================================
 *
 * Phase 9B
 *
 * Converts raw execution failures into structured
 * recovery intelligence.
 */
public final class FailureClassifier {

    public enum Type {
        VALIDATION,
        PERMISSION,
        CONFIRMATION_REQUIRED,
        NOT_FOUND,
        TEMPORARY,
        NETWORK,
        TIMEOUT,
        CANCELLED,
        DEPENDENCY,
        SAFETY,
        EXECUTION,
        UNKNOWN
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public static final class Analysis {

        private final Type type;
        private final Severity severity;
        private final boolean retryable;
        private final boolean requiresConfirmation;
        private final boolean requiresReplan;
        private final String reason;

        public Analysis(
                Type type,
                Severity severity,
                boolean retryable,
                boolean requiresConfirmation,
                boolean requiresReplan,
                String reason) {

            this.type =
                    type == null
                            ? Type.UNKNOWN
                            : type;

            this.severity =
                    severity == null
                            ? Severity.MEDIUM
                            : severity;

            this.retryable = retryable;
            this.requiresConfirmation =
                    requiresConfirmation;
            this.requiresReplan =
                    requiresReplan;

            this.reason =
                    reason == null
                            ? ""
                            : reason;
        }

        public Type getType() {
            return type;
        }

        public Severity getSeverity() {
            return severity;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public boolean requiresConfirmation() {
            return requiresConfirmation;
        }

        public boolean requiresReplan() {
            return requiresReplan;
        }

        public String getReason() {
            return reason;
        }
    }

    public Type classify(
            String message) {

        return analyze(message).getType();
    }

    public Analysis analyze(
            String message) {

        if (message == null ||
                message.trim().isEmpty()) {

            return new Analysis(
                    Type.UNKNOWN,
                    Severity.MEDIUM,
                    false,
                    false,
                    false,
                    "Unknown execution failure.");
        }

        String value =
                message
                        .toLowerCase(Locale.US)
                        .trim();

        if (contains(
                value,
                "cancel",
                "canceled",
                "cancelled")) {

            return new Analysis(
                    Type.CANCELLED,
                    Severity.LOW,
                    false,
                    false,
                    false,
                    message);
        }

        if (contains(
                value,
                "safety",
                "unsafe",
                "blocked by safety")) {

            return new Analysis(
                    Type.SAFETY,
                    Severity.CRITICAL,
                    false,
                    true,
                    false,
                    message);
        }

        if (contains(
                value,
                "permission",
                "access denied",
                "permission required")) {

            return new Analysis(
                    Type.PERMISSION,
                    Severity.HIGH,
                    false,
                    true,
                    false,
                    message);
        }

        if (contains(
                value,
                "confirmation",
                "confirm",
                "requires approval")) {

            return new Analysis(
                    Type.CONFIRMATION_REQUIRED,
                    Severity.HIGH,
                    false,
                    true,
                    false,
                    message);
        }

        if (contains(
                value,
                "timeout",
                "timed out")) {

            return new Analysis(
                    Type.TIMEOUT,
                    Severity.MEDIUM,
                    true,
                    false,
                    true,
                    message);
        }

        if (contains(
                value,
                "network",
                "internet",
                "connection",
                "socket")) {

            return new Analysis(
                    Type.NETWORK,
                    Severity.MEDIUM,
                    true,
                    false,
                    false,
                    message);
        }

        // A transient outage must remain retryable; otherwise the
        // generic "unavailable" match below incorrectly treats it as missing.
        if (value.contains("temporarily unavailable")) {

            return new Analysis(
                    Type.TEMPORARY,
                    Severity.MEDIUM,
                    true,
                    false,
                    false,
                    message);
        }

        if (contains(
                value,
                "not found",
                "does not exist",
                "missing",
                "unavailable")) {

            return new Analysis(
                    Type.NOT_FOUND,
                    Severity.MEDIUM,
                    false,
                    false,
                    true,
                    message);
        }

        if (contains(
                value,
                "dependency",
                "required first",
                "prerequisite")) {

            return new Analysis(
                    Type.DEPENDENCY,
                    Severity.HIGH,
                    false,
                    false,
                    true,
                    message);
        }

        if (contains(
                value,
                "invalid",
                "cannot be empty",
                "validation",
                "malformed")) {

            return new Analysis(
                    Type.VALIDATION,
                    Severity.LOW,
                    false,
                    false,
                    false,
                    message);
        }

        if (contains(
                value,
                "busy",
                "temporar",
                "try again",
                "rate limit")) {

            return new Analysis(
                    Type.TEMPORARY,
                    Severity.MEDIUM,
                    true,
                    false,
                    false,
                    message);
        }

        if (contains(
                value,
                "execution",
                "failed",
                "exception",
                "error")) {

            return new Analysis(
                    Type.EXECUTION,
                    Severity.HIGH,
                    true,
                    false,
                    true,
                    message);
        }

        return new Analysis(
                Type.UNKNOWN,
                Severity.MEDIUM,
                false,
                false,
                true,
                message);
    }

    public boolean isRetryable(
            Type type) {

        return type == Type.TEMPORARY ||
                type == Type.NETWORK ||
                type == Type.TIMEOUT ||
                type == Type.EXECUTION;
    }

    public boolean requiresConfirmation(
            Type type) {

        return type ==
                        Type.CONFIRMATION_REQUIRED ||

                type == Type.PERMISSION ||

                type == Type.SAFETY;
    }

    public boolean requiresReplan(
            Type type) {

        return type == Type.NOT_FOUND ||
                type == Type.DEPENDENCY ||
                type == Type.TIMEOUT ||
                type == Type.EXECUTION ||
                type == Type.UNKNOWN;
    }

    private boolean contains(
            String value,
            String... terms) {

        for (String term : terms) {

            if (value.contains(term)) {
                return true;
            }
        }

        return false;
    }
}