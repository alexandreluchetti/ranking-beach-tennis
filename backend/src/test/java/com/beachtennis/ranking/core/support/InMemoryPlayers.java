package com.beachtennis.ranking.core.support;

import com.beachtennis.ranking.core.domain.AuthIdentity;
import com.beachtennis.ranking.core.domain.DisplayName;
import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.port.PlayerRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryPlayers implements PlayerRepository {

    private final Map<UUID, Player> players = new LinkedHashMap<>();

    public Player add(String name) {
        return add(name, Instant.parse("2026-01-01T12:00:00Z"));
    }

    public Player add(String name, Instant registeredAt) {
        UUID id = UUID.randomUUID();
        Player player = new Player(id, new AuthIdentity("firebase", "uid-" + id), null, new DisplayName(name),
                registeredAt, registeredAt);
        players.put(id, player);
        return player;
    }

    @Override
    public Optional<Player> findByIdentity(AuthIdentity identity) {
        return players.values().stream().filter(p -> p.identity().equals(identity)).findFirst();
    }

    @Override
    public List<Player> findAllByIds(Collection<UUID> ids) {
        return ids.stream().map(players::get).filter(p -> p != null).toList();
    }

    @Override
    public List<Player> findAll() {
        return List.copyOf(players.values());
    }

    @Override
    public void insertIfAbsent(Player player) {
        players.putIfAbsent(player.id(), player);
    }

    @Override
    public Player update(Player player) {
        players.put(player.id(), player);
        return player;
    }
}
