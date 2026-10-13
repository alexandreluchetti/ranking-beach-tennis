package com.lucchetta.core.usecase.ranking;

import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.SeasonStatus;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.stats.PlayerStats;
import com.lucchetta.core.stats.RankingEntry;
import com.lucchetta.core.stats.StatisticsCalculator;
import com.lucchetta.core.usecase.match.MatchDetails;
import com.lucchetta.core.usecase.match.MatchDetailsAssembler;
import com.lucchetta.core.usecase.season.QuerySeasons;
import com.lucchetta.core.usecase.season.SeasonView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A player's profile: current season standing, career statistics and past season standings.
 * The match list itself comes from the match history filtered by player.
 */
public class GetPlayerProfile {

    /**
     * @param currentSeason null when there is no active season
     * @param career        all valid matches ever, inside or outside seasons; points only from
     *                      matches that counted
     * @param pastSeasons   closed seasons the player is ranked in, most recent first
     */
    public record PlayerProfile(Player player, SeasonStanding currentSeason, PlayerStats career,
                                List<SeasonStanding> pastSeasons) {
    }

    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final QuerySeasons querySeasons;
    private final MatchDetailsAssembler assembler;

    public GetPlayerProfile(PlayerRepository playerRepository,
                            MatchRepository matchRepository,
                            QuerySeasons querySeasons,
                            MatchDetailsAssembler assembler) {
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.querySeasons = querySeasons;
        this.assembler = assembler;
    }

    public PlayerProfile execute(UUID playerId) {
        List<Player> players = playerRepository.findAll();
        Player player = players.stream().filter(p -> p.id().equals(playerId)).findFirst()
                .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND"));
        // Every match is needed: rankings depend on the other players' results too.
        List<MatchDetails> matches = assembler.assembleAll(matchRepository.findPlayed(null, null, null));

        SeasonStanding current = null;
        List<SeasonStanding> past = new ArrayList<>();
        for (SeasonView view : querySeasons.list()) {
            if (view.status() == SeasonStatus.PLANNED) {
                continue;
            }
            SeasonStanding standing = standingOf(view, matches, players, playerId);
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

    private static SeasonStanding standingOf(SeasonView view, List<MatchDetails> matches, List<Player> players,
                                             UUID playerId) {
        List<RankingEntry> ranking = SeasonRankingBuilder.build(view.season(), matches, players);
        return ranking.stream()
                .filter(e -> e.playerId().equals(playerId))
                .findFirst()
                .map(e -> new SeasonStanding(view, e.position(), ranking.size(), e.stats()))
                .orElse(null);
    }
}
