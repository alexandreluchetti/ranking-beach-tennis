package com.lucchetta.core.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A registered user, who takes part in the rankings of their leagues.
 *
 * @param displayName how the user wants to be called (nickname): shown in rankings and matches
 * @param fullName    first and last name (D-024); null until the user fills it in
 */
public record Player(
        UUID id,
        AuthIdentity identity,
        String email,
        DisplayName displayName,
        Instant createdAt,
        Instant updatedAt,
        FullName fullName) {

    public Player {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** A player without a full name yet. */
    public Player(UUID id, AuthIdentity identity, String email, DisplayName displayName, Instant createdAt,
                  Instant updatedAt) {
        this(id, identity, email, displayName, createdAt, updatedAt, null);
    }

    public Player rename(DisplayName newName, Instant now) {
        return new Player(id, identity, email, newName, createdAt, now, fullName);
    }

    public Player withFullName(FullName newFullName, Instant now) {
        return new Player(id, identity, email, displayName, createdAt, now, newFullName);
    }
}
