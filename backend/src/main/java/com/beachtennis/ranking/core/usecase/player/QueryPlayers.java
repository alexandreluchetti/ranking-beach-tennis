package com.beachtennis.ranking.core.usecase.player;

import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.port.PlayerRepository;

import java.util.Comparator;
import java.util.List;

public class QueryPlayers {

    private final PlayerRepository playerRepository;

    public QueryPlayers(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    /** All registered players, by display name. */
    public List<Player> list() {
        return playerRepository.findAll().stream()
                .sorted(Comparator.comparing(p -> p.displayName().value(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
