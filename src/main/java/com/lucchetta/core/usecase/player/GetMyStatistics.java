package com.lucchetta.core.usecase.player;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.stats.PlayerStats;
import com.lucchetta.core.stats.StatisticsCalculator;
import com.lucchetta.core.usecase.match.MatchDetailsAssembler;

/**
 * The caller's global statistics over every league they played in (decision D-022): matches,
 * wins, losses, games and win rate. Only the caller can see their own; points are left out,
 * since each league has its own ranking.
 */
public class GetMyStatistics {

    private final GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;
    private final MatchRepository matchRepository;
    private final MatchDetailsAssembler assembler;

    public GetMyStatistics(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                           MatchRepository matchRepository,
                           MatchDetailsAssembler assembler) {
        this.getOrCreateCurrentPlayer = getOrCreateCurrentPlayer;
        this.matchRepository = matchRepository;
        this.assembler = assembler;
    }

    /** Statistics of every valid match of the caller, in any league; points are not meaningful here. */
    public PlayerStats execute(AuthenticatedUser user) {
        Player player = getOrCreateCurrentPlayer.execute(user);
        return StatisticsCalculator.byPlayer(assembler.assembleAll(
                        matchRepository.findPlayed(null, null, null, player.id())))
                .getOrDefault(player.id(), PlayerStats.EMPTY);
    }
}
