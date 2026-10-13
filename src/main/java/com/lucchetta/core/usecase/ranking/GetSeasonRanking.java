package com.lucchetta.core.usecase.ranking;

import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.stats.RankingEntry;
import com.lucchetta.core.usecase.match.MatchDetailsAssembler;
import com.lucchetta.core.usecase.season.QuerySeasons;
import com.lucchetta.core.usecase.season.SeasonView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ranking of one season, computed from its matches at query time (D-007).
 */
public class GetSeasonRanking {

    public record SeasonRanking(SeasonView season, List<RankingEntry> entries) {
    }

    private final QuerySeasons querySeasons;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final MatchDetailsAssembler assembler;

    public GetSeasonRanking(QuerySeasons querySeasons,
                            MatchRepository matchRepository,
                            PlayerRepository playerRepository,
                            MatchDetailsAssembler assembler) {
        this.querySeasons = querySeasons;
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.assembler = assembler;
    }

    public SeasonRanking bySeasonId(UUID seasonId) {
        return rankingOf(querySeasons.byId(seasonId));
    }

    /** Ranking of the active season, if there is one. */
    public Optional<SeasonRanking> current() {
        return querySeasons.current().map(this::rankingOf);
    }

    private SeasonRanking rankingOf(SeasonView view) {
        Season season = view.season();
        Instant from = season.startDate().atStartOfDay(BusinessCalendar.ZONE).toInstant();
        Instant to = season.endDate().plusDays(1).atStartOfDay(BusinessCalendar.ZONE).toInstant();
        var matches = assembler.assembleAll(matchRepository.findValidPlayedBetween(from, to));
        return new SeasonRanking(view, SeasonRankingBuilder.build(season, matches, playerRepository.findAll()));
    }
}
