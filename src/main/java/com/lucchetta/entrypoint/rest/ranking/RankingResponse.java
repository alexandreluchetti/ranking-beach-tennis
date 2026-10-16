package com.lucchetta.entrypoint.rest.ranking;

import com.lucchetta.core.usecase.ranking.GetSeasonRanking;
import com.lucchetta.entrypoint.rest.season.SeasonResponse;

import java.util.List;
import java.util.UUID;

/**
 * @param entries ordered by D-013; every {@code position} is distinct (1, 2, 3, ...)
 */
public record RankingResponse(SeasonResponse season, List<Entry> entries) {

    public record Entry(int position, UUID playerId, String displayName, int points, int wins, int losses,
                        int matchesPlayed, int gameDifference) {
    }

    static RankingResponse from(GetSeasonRanking.SeasonRanking ranking) {
        return new RankingResponse(SeasonResponse.from(ranking.season()), ranking.entries().stream()
                .map(e -> new Entry(e.position(), e.playerId(), e.displayName(), e.stats().points(), e.stats().wins(),
                        e.stats().losses(), e.stats().matchesPlayed(), e.stats().gameDifference()))
                .toList());
    }
}
