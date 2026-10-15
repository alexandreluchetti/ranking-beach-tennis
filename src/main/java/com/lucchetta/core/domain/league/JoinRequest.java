package com.lucchetta.core.domain.league;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A user's request to join a private league, answered by any of its league administrators.
 *
 * @param decidedBy who accepted or rejected it; null while pending or when cancelled
 */
public record JoinRequest(
        UUID id,
        UUID leagueId,
        UUID playerId,
        LeagueEntryStatus status,
        Instant createdAt,
        UUID decidedBy,
        Instant decidedAt) {

    public JoinRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(leagueId, "leagueId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static JoinRequest pending(UUID leagueId, UUID playerId, Instant now) {
        return new JoinRequest(UUID.randomUUID(), leagueId, playerId, LeagueEntryStatus.PENDING, now, null, null);
    }

    public boolean pending() {
        return status == LeagueEntryStatus.PENDING;
    }
}
