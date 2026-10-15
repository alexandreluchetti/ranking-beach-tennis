package com.lucchetta.core.usecase.ranking;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.stats.RankingEntry;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.match.MatchDetails;
import com.lucchetta.core.usecase.match.MatchDetailsAssembler;
import com.lucchetta.core.usecase.season.QuerySeasons;
import com.lucchetta.core.usecase.season.SeasonView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ranking of one season of a league, computed from its matches at query time (D-007). League
 * members only (D-020).
 */
public class GetSeasonRanking {

    public record SeasonRanking(SeasonView season, List<RankingEntry> entries) {
    }

    private final LeagueAccess leagueAccess;
    private final QuerySeasons querySeasons;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final LeagueMemberRepository memberRepository;
    private final MatchDetailsAssembler assembler;

    public GetSeasonRanking(LeagueAccess leagueAccess,
                            QuerySeasons querySeasons,
                            MatchRepository matchRepository,
                            PlayerRepository playerRepository,
                            LeagueMemberRepository memberRepository,
                            MatchDetailsAssembler assembler) {
        this.leagueAccess = leagueAccess;
        this.querySeasons = querySeasons;
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.memberRepository = memberRepository;
        this.assembler = assembler;
    }

    public SeasonRanking bySeasonId(AuthenticatedUser user, UUID leagueId, UUID seasonId) {
        leagueAccess.requireMember(user, leagueId);
        return rankingOf(querySeasons.byId(leagueId, seasonId));
    }

    /** Ranking of the league's active season, if there is one. */
    public Optional<SeasonRanking> current(AuthenticatedUser user, UUID leagueId) {
        leagueAccess.requireMember(user, leagueId);
        return querySeasons.current(leagueId).map(this::rankingOf);
    }

    private SeasonRanking rankingOf(SeasonView view) {
        Season season = view.season();
        Instant from = season.startDate().atStartOfDay(BusinessCalendar.ZONE).toInstant();
        Instant to = season.endDate().plusDays(1).atStartOfDay(BusinessCalendar.ZONE).toInstant();
        List<MatchDetails> matches = assembler.assembleAll(
                matchRepository.findValidPlayedBetween(season.leagueId(), from, to));
        List<LeagueMember> members = memberRepository.findByLeague(season.leagueId());
        return new SeasonRanking(view, SeasonRankingBuilder.build(season, matches, members,
                SeasonRankingBuilder.playersOf(playerRepository, members, matches)));
    }
}
