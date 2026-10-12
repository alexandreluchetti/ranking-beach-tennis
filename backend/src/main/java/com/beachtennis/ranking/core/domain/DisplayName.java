package com.beachtennis.ranking.core.domain;

import com.beachtennis.ranking.core.exception.InvalidDisplayNameException;

/**
 * Name shown in rankings and match history. Surrounding whitespace is removed and internal
 * whitespace is collapsed.
 */
public record DisplayName(String value) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 40;

    public DisplayName {
        if (value == null) {
            throw new InvalidDisplayNameException();
        }
        value = value.strip().replaceAll("\\s+", " ");
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new InvalidDisplayNameException();
        }
    }
}
