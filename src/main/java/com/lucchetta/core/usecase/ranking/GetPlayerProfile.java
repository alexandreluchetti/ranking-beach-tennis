package com.lucchetta.core.usecase.ranking;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.SeasonStatus;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.stats.PlayerStats;
import com.lucchetta.core.stats.RankingEntry;
import com.lucchetta.core.stats.StatisticsCalculator;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.match.MatchDetails;
import com.lucchetta.core.usecase.match.MatchDetailsAssembler;
import com.lucchetta.core.usecase.season.QuerySeasons;
import com.lucchetta.core.usecase.season.SeasonView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A player's profile inside a league: current season standing, statistics over the league's
 * matches and past season standings (decision D-021). League members only; players are visible
 * while they are members or once they played in the league. The match list itself comes from
 * the league's match history filtered by player.
 */
public class GetPlayerProfile {

    /**
     * @param currentSeason null when the league has no active season
     * @param career        all valid matches in the league, inside or outside seasons; points
     *                      only from matches that counted
     * @param pastSeasons   closed seasons the player is ranked in, most recent first
     */
    public record PlayerProfile(Player player, SeasonStanding currentSeason, PlayerStats career,
                                List<SeasonStanding> pastSeasons) {
    }

    private final LeagueAccess leagueAccess;
    private final PlayerRepository playerRepository;
    private final LeagueMemberRepository memberRepository;
    private final MatchRepository matchRepository;
    private final QuerySeasons querySeasons;
    private final MatchDetailsAssembler assembler;

    public GetPlayerProfile(LeagueAccess leagueAccess,
                            PlayerRepository playerRepository,
                            LeagueMemberRepository memberRepository,
                            MatchRepository matchRepository,
                            QuerySeasons querySeasons,
                            MatchDetailsAssembler assembler) {
        this.leagueAccess = leagueAccess;
        this.playerRepository = playerRepository;
        this.memberRepository = memberRepository;
        this.matchRepository = matchRepository;
        this.querySeasons = querySeasons;
        this.assembler = assembler;
    }

    public PlayerProfile execute(AuthenticatedUser user, UUID leagueId, UUID playerId) {
        leagueAccess.requireMember(user, leagueId);
        // Every match of the league is needed: rankings depend on the other players' results too.
        List<MatchDetails> matches = assembler.assembleAll(matchRepository.findPlayed(leagueId, null, null, null));
        List<LeagueMember> members = memberRepository.findByLeague(leagueId);
        List<Player> players = SeasonRankingBuilder.playersOf(playerRepository, members, matches);
        Player player = players.stream().filter(p -> p.id().equals(playerId)).findFirst()
                .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND"));

        SeasonStanding current = null;
        List<SeasonStanding> past = new ArrayList<>();
        for (SeasonView view : querySeasons.list(leagueId)) {
            if (view.status() == SeasonStatus.PLANNED) {
                continue;
            }
            SeasonStanding standing = standingOf(view, matches, members, players, playerId);
            if (view.status() == SeasonStatus.ACTIVE) {
                current = standing;
            } else if (standing != null) {
                past.add(standing);
            }
        }

        List<MatchDetails> own = matches.stream()
                .filter(d -> d.match().participants().stream().anyMatch(p -> playerId.equals(p.playerId())))
                .toList();
        PlayerStats career = StatisticsCalculator.byPlayer(own).getOrDefault(playerId, PlayerStats.EMPTY);
        return new PlayerProfile(player, current, career, past);
    }

    private static SeasonStanding standingOf(SeasonView view, List<MatchDetails> matches, List<LeagueMember> members,
                                             List<Player> players, UUID playerId) {
        List<RankingEntry> ranking = SeasonRankingBuilder.build(view.season(), matches, members, players);
        return ranking.stream()
                .filter(e -> e.playerId().equals(playerId))
                .findFirst()
                .map(e -> new SeasonStanding(view, e.position(), ranking.size(), e.stats()))
                .orElse(null);
    }
}
