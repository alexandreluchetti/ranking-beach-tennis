package com.lucchetta.core.support;

import com.lucchetta.core.domain.AuthIdentity;
import com.lucchetta.core.domain.DisplayName;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.port.PlayerRepository;

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

    public List<Player> findAll() {
        return List.copyOf(players.values());
    }

    @Override
    public List<Player> findByEmail(String email) {
        return players.values().stream().filter(p -> p.email() != null && p.email().equalsIgnoreCase(email)).toList();
    }

    /** Stores a copy of the player with the given e-mail. */
    public Player withEmail(Player player, String email) {
        Player changed = new Player(player.id(), player.identity(), email, player.displayName(), player.createdAt(),
                player.updatedAt());
        players.put(changed.id(), changed);
        return changed;
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
