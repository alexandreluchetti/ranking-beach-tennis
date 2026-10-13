package com.beachtennis.ranking.core.port;

import com.beachtennis.ranking.core.domain.AuthIdentity;
import com.beachtennis.ranking.core.domain.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository {

    Optional<Player> findByIdentity(AuthIdentity identity);

    List<Player> findAllByIds(Collection<UUID> ids);

    List<Player> findAll();

    /**
     * Inserts the player unless one with the same identity already exists. Safe under concurrent
     * first logins of the same user.
     */
    void insertIfAbsent(Player player);

    Player update(Player player);
}
