package com.lucchetta.core.usecase.player;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.DisplayName;
import com.lucchetta.core.domain.FullName;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.port.PlayerRepository;

import java.time.Clock;
import java.time.Instant;

/**
 * Changes the caller's display name (how they want to be called) and full name (D-024). History
 * and statistics reference the player id, so they are preserved.
 */
public class UpdateProfile {

    /** Null fields are left unchanged. */
    public record Command(String displayName, String fullName) {
    }

    private final GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;
    private final PlayerRepository playerRepository;
    private final Clock clock;

    public UpdateProfile(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                         PlayerRepository playerRepository,
                         Clock clock) {
        this.getOrCreateCurrentPlayer = getOrCreateCurrentPlayer;
        this.playerRepository = playerRepository;
        this.clock = clock;
    }

    public Player execute(AuthenticatedUser user, Command command) {
        DisplayName displayName = command.displayName() == null ? null : new DisplayName(command.displayName());
        FullName fullName = command.fullName() == null ? null : new FullName(command.fullName());
        Player current = getOrCreateCurrentPlayer.execute(user);
        Instant now = clock.instant();
        Player changed = current;
        if (displayName != null && !displayName.equals(current.displayName())) {
            changed = changed.rename(displayName, now);
        }
        if (fullName != null && !fullName.equals(current.fullName())) {
            changed = changed.withFullName(fullName, now);
        }
        return changed == current ? current : playerRepository.update(changed);
    }
}
