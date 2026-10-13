package com.lucchetta.core.usecase.match;

import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.Team;
import com.lucchetta.core.scoring.RankingEligibility;

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
