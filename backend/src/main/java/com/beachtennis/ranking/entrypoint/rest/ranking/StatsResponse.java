package com.beachtennis.ranking.entrypoint.rest.ranking;

import com.beachtennis.ranking.core.stats.PlayerStats;

public record StatsResponse(int points, int wins, int losses, int matchesPlayed, int gamesWon, int gamesLost,
                            int gameDifference) {

    public static StatsResponse from(PlayerStats stats) {
        return new StatsResponse(stats.points(), stats.wins(), stats.losses(), stats.matchesPlayed(),
                stats.gamesWon(), stats.gamesLost(), stats.gameDifference());
    }
}
