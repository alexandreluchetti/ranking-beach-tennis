package com.lucchetta.core.scoring;

import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchStatus;

/**
 * Decides whether a match counts for the ranking. Kept apart from the scoring formula and from
 * statistics rules (a guest match counts for career statistics only, D-004).
 * When several reasons apply, the first in this order is reported: voided, guest, no season.
 */
public class RankingEligibilityPolicy {

    /**
     * @param season the season containing the match date, or null if there is none
     */
    public RankingEligibility evaluate(Match match, Season season) {
        if (match.status() == MatchStatus.VOIDED) {
            return RankingEligibility.notCounted(NotCountedReason.VOIDED);
        }
        if (match.hasGuest()) {
            return RankingEligibility.notCounted(NotCountedReason.GUEST_PRESENT);
        }
        if (season == null) {
            return RankingEligibility.notCounted(NotCountedReason.NO_SEASON);
        }
        return RankingEligibility.COUNTED;
    }
}
