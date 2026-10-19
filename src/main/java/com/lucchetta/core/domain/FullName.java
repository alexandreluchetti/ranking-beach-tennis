package com.lucchetta.core.domain;

import com.lucchetta.core.exception.RuleViolationException;

/**
 * The user's full name (decision D-024): at least a first and a last name, 3 to 100 characters.
 * Surrounding whitespace is removed and internal whitespace is collapsed. Unlike the display name,
 * it is shown only to the user and to the administrators of their leagues.
 */
public record FullName(String value) {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 100;

    public FullName {
        String normalized = value == null ? "" : value.strip().replaceAll("\s+", " ");
        if (normalized.length() < MIN_LENGTH || normalized.length() > MAX_LENGTH
                || normalized.split(" ").length < 2) {
            throw new RuleViolationException("INVALID_FULL_NAME");
        }
        value = normalized;
    }

    /** The full name, or null when {@code value} is not a valid one. */
    public static FullName orNull(String value) {
        try {
            return new FullName(value);
        } catch (RuleViolationException e) {
            return null;
        }
    }
}
