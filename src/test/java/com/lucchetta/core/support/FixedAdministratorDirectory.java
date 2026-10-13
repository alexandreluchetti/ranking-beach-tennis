package com.lucchetta.core.support;

import com.lucchetta.core.port.AdministratorDirectory;

import java.util.Optional;
import java.util.UUID;

public record FixedAdministratorDirectory(UUID adminPlayerId) implements AdministratorDirectory {

    @Override
    public Optional<UUID> administratorPlayerId() {
        return Optional.ofNullable(adminPlayerId);
    }
}
