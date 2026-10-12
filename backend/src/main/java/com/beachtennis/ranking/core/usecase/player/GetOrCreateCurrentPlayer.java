package com.beachtennis.ranking.core.usecase.player;

import com.beachtennis.ranking.core.domain.AuthenticatedUser;
import com.beachtennis.ranking.core.domain.DisplayName;
import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.exception.InvalidDisplayNameException;
import com.beachtennis.ranking.core.port.PlayerRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Returns the player profile of the caller, creating it on first access. The initial display
 * name comes from the provider's name, then from the e-mail local part, then a default.
 */
public class GetOrCreateCurrentPlayer {

    static final String DEFAULT_DISPLAY_NAME = "Jogador";

    private final PlayerRepository playerRepository;
    private final Clock clock;

    public GetOrCreateCurrentPlayer(PlayerRepository playerRepository, Clock clock) {
        this.playerRepository = playerRepository;
        this.clock = clock;
    }

    public Player execute(AuthenticatedUser user) {
        return playerRepository.findByIdentity(user.identity())
                .orElseGet(() -> create(user));
    }

    private Player create(AuthenticatedUser user) {
        Instant now = clock.instant();
        Player candidate = new Player(
                UUID.randomUUID(), user.identity(), user.email(), initialName(user), now, now);
        playerRepository.insertIfAbsent(candidate);
        return playerRepository.findByIdentity(user.identity())
                .orElseThrow(() -> new IllegalStateException("Player not found after insert"));
    }

    private static DisplayName initialName(AuthenticatedUser user) {
        DisplayName fromName = tryCreate(user.name());
        if (fromName != null) {
            return fromName;
        }
        if (user.email() != null && user.email().contains("@")) {
            DisplayName fromEmail = tryCreate(user.email().substring(0, user.email().indexOf('@')));
            if (fromEmail != null) {
                return fromEmail;
            }
        }
        return new DisplayName(DEFAULT_DISPLAY_NAME);
    }

    private static DisplayName tryCreate(String value) {
        if (value == null) {
            return null;
        }
        String truncated = value.strip();
        if (truncated.length() > DisplayName.MAX_LENGTH) {
            truncated = truncated.substring(0, DisplayName.MAX_LENGTH);
        }
        try {
            return new DisplayName(truncated);
        } catch (InvalidDisplayNameException e) {
            return null;
        }
    }
}
