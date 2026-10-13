package com.lucchetta.core.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A registered user. Every player takes part in the ranking (decision D-001).
 */
public record Player(
        UUID id,
        AuthIdentity identity,
        String email,
        DisplayName displayName,
        Instant createdAt,
        Instant updatedAt) {

    public Player {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public Player rename(DisplayName newName, Instant now) {
        return new Player(id, identity, email, newName, createdAt, now);
    }
}
