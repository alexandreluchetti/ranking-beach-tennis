package com.lucchetta.core.domain.league;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Membership of a player in a league. {@code joinedAt} decides from which seasons on the player
 * is listed in the league's rankings.
 */
public record LeagueMember(UUID leagueId, UUID playerId, LeagueRole role, Instant joinedAt) {

    public LeagueMember {
        Objects.requireNonNull(leagueId, "leagueId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }

    public boolean admin() {
        return role == LeagueRole.ADMIN;
    }

    public LeagueMember withRole(LeagueRole newRole) {
        return new LeagueMember(leagueId, playerId, newRole, joinedAt);
    }
}
