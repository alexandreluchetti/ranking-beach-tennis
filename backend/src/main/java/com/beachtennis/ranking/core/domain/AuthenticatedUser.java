package com.beachtennis.ranking.core.domain;

import java.util.Objects;

/**
 * The caller of a use case, as established by the authentication layer.
 *
 * @param identity stable identity at the authentication provider
 * @param email    e-mail reported by the provider, may be null
 * @param name     name reported by the provider, may be null
 * @param admin    whether the caller is the configured administrator
 */
public record AuthenticatedUser(AuthIdentity identity, String email, String name, boolean admin) {

    public AuthenticatedUser {
        Objects.requireNonNull(identity, "identity");
    }
}
