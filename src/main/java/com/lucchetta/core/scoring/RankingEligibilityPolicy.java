package com.lucchetta.core.scoring;

import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchStatus;

/**
 * Decides whether a match counts for the ranking. Kept apart from the scoring formula and from
 * statistics rules. Guests do not matter (D-004): every valid match inside a season counts.
 * When both reasons apply, the first in this order is reported: voided, no season.
 */
public class RankingEligibilityPolicy {

    /**
     * @param season the season containing the match date, or null if there is none
     */
    public RankingEligibility evaluate(Match match, Season season) {
        if (match.status() == MatchStatus.VOIDED) {
            return RankingEligibility.notCounted(NotCountedReason.VOIDED);
        }
        if (season == null) {
            return RankingEligibility.notCounted(NotCountedReason.NO_SEASON);
        }
        return RankingEligibility.COUNTED;
    }
}
