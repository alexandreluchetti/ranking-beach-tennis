package com.beachtennis.ranking.core.scoring;

/**
 * Whether a match counts for the ranking and, if not, why.
 *
 * @param reason null when the match counts
 */
public record RankingEligibility(boolean counted, NotCountedReason reason) {

    public static final RankingEligibility COUNTED = new RankingEligibility(true, null);

    public static RankingEligibility notCounted(NotCountedReason reason) {
        return new RankingEligibility(false, reason);
    }
}
