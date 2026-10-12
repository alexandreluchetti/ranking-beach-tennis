package com.beachtennis.ranking.core.port;

import com.beachtennis.ranking.core.domain.AuthIdentity;
import com.beachtennis.ranking.core.domain.Player;

import java.util.Optional;

public interface PlayerRepository {

    Optional<Player> findByIdentity(AuthIdentity identity);

    /**
     * Inserts the player unless one with the same identity already exists. Safe under concurrent
     * first logins of the same user.
     */
    void insertIfAbsent(Player player);

    Player update(Player player);
}
