package com.lucchetta.core.domain.league;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An in-app invitation from a league administrator to a registered user, who accepts or rejects
 * it (decision D-019). No e-mail is sent.
 *
 * @param playerId the invitee
 */
public record LeagueInvitation(
        UUID id,
        UUID leagueId,
        UUID playerId,
        UUID invitedBy,
        LeagueEntryStatus status,
        Instant createdAt,
        Instant decidedAt) {

    public LeagueInvitation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(leagueId, "leagueId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(invitedBy, "invitedBy");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static LeagueInvitation pending(UUID leagueId, UUID playerId, UUID invitedBy, Instant now) {
        return new LeagueInvitation(UUID.randomUUID(), leagueId, playerId, invitedBy, LeagueEntryStatus.PENDING, now,
                null);
    }

    public boolean pending() {
        return status == LeagueEntryStatus.PENDING;
    }
}
