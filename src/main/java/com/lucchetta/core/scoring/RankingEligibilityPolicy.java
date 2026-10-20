package com.lucchetta.core.scoring;

import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchStatus;

/**
 * Decides whether a match counts for the ranking. Kept apart from the scoring formula and from
 * statistics rules. Guests do not matter (D-004): every valid match inside a season counts.
 * Only matches played on one of the season's playing days count (D-025). When several reasons
 * apply, the first in this order is reported: voided, no season, not a playing day.
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
        if (!season.isPlayingDay(match.playedOn())) {
            return RankingEligibility.notCounted(NotCountedReason.NOT_A_PLAYING_DAY);
        }
        return RankingEligibility.COUNTED;
    }
}
