package com.lucchetta.core.domain;

import java.util.Objects;

/**
 * Stable identity of a user at the authentication provider. Isolates the domain from the
 * concrete provider (currently Firebase).
 */
public record AuthIdentity(String provider, String subject) {

    public AuthIdentity {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(subject, "subject");
        if (provider.isBlank() || subject.isBlank()) {
            throw new IllegalArgumentException("provider and subject must not be blank");
        }
    }
}
