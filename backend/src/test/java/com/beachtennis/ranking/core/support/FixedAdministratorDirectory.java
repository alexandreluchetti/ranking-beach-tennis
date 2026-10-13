package com.beachtennis.ranking.core.support;

import com.beachtennis.ranking.core.port.AdministratorDirectory;

import java.util.Optional;
import java.util.UUID;

public record FixedAdministratorDirectory(UUID adminPlayerId) implements AdministratorDirectory {

    @Override
    public Optional<UUID> administratorPlayerId() {
        return Optional.ofNullable(adminPlayerId);
    }
}
