package com.lucchetta.core.usecase.player;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.DisplayName;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.port.PlayerRepository;

import java.time.Clock;

/**
 * Changes the caller's display name. History and statistics reference the player id, so they are
 * preserved.
 */
public class UpdateDisplayName {

    private final GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;
    private final PlayerRepository playerRepository;
    private final Clock clock;

    public UpdateDisplayName(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                             PlayerRepository playerRepository,
                             Clock clock) {
        this.getOrCreateCurrentPlayer = getOrCreateCurrentPlayer;
        this.playerRepository = playerRepository;
        this.clock = clock;
    }

    public Player execute(AuthenticatedUser user, String newName) {
        DisplayName displayName = new DisplayName(newName);
        Player current = getOrCreateCurrentPlayer.execute(user);
        if (current.displayName().equals(displayName)) {
            return current;
        }
        return playerRepository.update(current.rename(displayName, clock.instant()));
    }
}
