package com.lucchetta.core.domain.league;

import java.util.UUID;

/**
 * The "Beach Tennis Arena" league, created by migration V6 with everything that existed before
 * leagues (decision D-023). The endpoints without a league are bound to it until the app with
 * leagues is published.
 */
public final class DefaultLeague {

    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String NAME = "Beach Tennis Arena";

    private DefaultLeague() {
    }
}
