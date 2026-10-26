package com.lucchetta.core.usecase.ranking;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.stats.RankingEntry;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.match.LeagueMatches;
import com.lucchetta.core.usecase.match.MatchDetails;
import com.lucchetta.core.usecase.season.QuerySeasons;
import com.lucchetta.core.usecase.season.SeasonView;

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
    private final PlayerRepository playerRepository;
    private final LeagueMemberRepository memberRepository;
    private final LeagueMatches leagueMatches;

    public GetSeasonRanking(LeagueAccess leagueAccess,
                            QuerySeasons querySeasons,
                            PlayerRepository playerRepository,
                            LeagueMemberRepository memberRepository,
                            LeagueMatches leagueMatches) {
        this.leagueAccess = leagueAccess;
        this.querySeasons = querySeasons;
        this.playerRepository = playerRepository;
        this.memberRepository = memberRepository;
        this.leagueMatches = leagueMatches;
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
        // All the league's matches: the builder keeps the season's matches that count.
        List<MatchDetails> matches = leagueMatches.all(season.leagueId());
        List<LeagueMember> members = memberRepository.findByLeague(season.leagueId());
        return new SeasonRanking(view, SeasonRankingBuilder.build(season, matches, members,
                SeasonRankingBuilder.playersOf(playerRepository, members, matches)));
    }
}
