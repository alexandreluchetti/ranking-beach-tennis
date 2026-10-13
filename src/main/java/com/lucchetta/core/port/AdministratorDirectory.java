package com.lucchetta.core.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Finds the player profile of the configured administrator, if they have signed in at least once.
 */
public interface AdministratorDirectory {

    Optional<UUID> administratorPlayerId();
}
