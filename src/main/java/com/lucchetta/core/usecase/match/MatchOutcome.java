package com.lucchetta.core.usecase.match;

import com.lucchetta.core.domain.match.MatchStatus;

/**
 * History filter categories. Exactly one applies to each match, so a voided match is never
 * confused with a valid match that does not score.
 */
public enum MatchOutcome {
    /** Valid and counted for the ranking. */
    COUNTED,
    /** Valid but not counted (guest present or outside any season). */
    NOT_COUNTED,
    /** Voided by the administrator. */
    VOIDED;

    public static MatchOutcome of(MatchDetails details) {
        if (details.match().status() == MatchStatus.VOIDED) {
            return VOIDED;
        }
        return details.eligibility().counted() ? COUNTED : NOT_COUNTED;
    }
}
