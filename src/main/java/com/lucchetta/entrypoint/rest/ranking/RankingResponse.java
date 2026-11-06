package com.lucchetta.entrypoint.rest.ranking;

import com.lucchetta.core.stats.TournamentRecord;
import com.lucchetta.core.usecase.ranking.GetSeasonRanking;
import com.lucchetta.entrypoint.rest.season.SeasonResponse;

import java.util.List;
import java.util.UUID;

/**
 * @param entries ordered by D-013, or by D-028 in a daily tournament season; every
 *                {@code position} is distinct (1, 2, 3, ...)
 */
public record RankingResponse(SeasonResponse season, List<Entry> entries) {

    /**
     * @param points       points per match, or placement points in a daily tournament season
     * @param stagesPlayed finished stages played; daily tournament only, null otherwise
     * @param titles       stages won; daily tournament only
     * @param finals       finals played; daily tournament only
     */
    public record Entry(int position, UUID playerId, String displayName, int points, int wins, int losses,
                        int matchesPlayed, int gameDifference, Integer stagesPlayed, Integer titles, Integer finals) {
    }

    static RankingResponse from(GetSeasonRanking.SeasonRanking ranking) {
        return new RankingResponse(SeasonResponse.from(ranking.season()), ranking.entries().stream()
                .map(e -> {
                    TournamentRecord t = e.tournament();
                    return new Entry(e.position(), e.playerId(), e.displayName(), e.stats().points(),
                            e.stats().wins(), e.stats().losses(), e.stats().matchesPlayed(),
                            e.stats().gameDifference(), t == null ? null : t.stages(), t == null ? null : t.titles(),
                            t == null ? null : t.finals());
                })
                .toList());
    }
}
