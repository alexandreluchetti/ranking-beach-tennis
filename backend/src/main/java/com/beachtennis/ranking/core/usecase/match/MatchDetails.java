package com.beachtennis.ranking.core.usecase.match;

import com.beachtennis.ranking.core.domain.Season;
import com.beachtennis.ranking.core.domain.match.Match;
import com.beachtennis.ranking.core.domain.match.Team;
import com.beachtennis.ranking.core.scoring.RankingEligibility;

import java.util.Map;
import java.util.UUID;

/**
 * A match with everything needed to present it: participant names, its season, whether it
 * counts for the ranking and the points each player of each team receives (0 when it does not
 * count).
 *
 * @param season null when the match date is outside every season
 */
public record MatchDetails(
        Match match,
        Season season,
        RankingEligibility eligibility,
        Map<UUID, String> playerNames,
        Map<Team, Integer> pointsPerPlayer) {

    public int pointsFor(Team team) {
        return pointsPerPlayer.getOrDefault(team, 0);
    }
}
