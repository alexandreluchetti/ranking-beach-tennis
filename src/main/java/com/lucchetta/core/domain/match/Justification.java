package com.lucchetta.core.domain.match;

import com.lucchetta.core.exception.RuleViolationException;

/**
 * Reason given by the administrator for an operation on a match.
 */
public final class Justification {

    public static final int MAX_LENGTH = 500;

    private Justification() {
    }

    /** Required: voiding and reverting a void. */
    public static String required(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new RuleViolationException("REASON_REQUIRED");
        }
        return normalized;
    }

    /** Optional: corrections. */
    public static String optional(String value) {
        return normalize(value);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_LENGTH) {
            throw new RuleViolationException("REASON_TOO_LONG");
        }
        return normalized;
    }
}
