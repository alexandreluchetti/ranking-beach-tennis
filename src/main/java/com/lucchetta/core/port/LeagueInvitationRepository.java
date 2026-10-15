package com.lucchetta.core.port;

import com.lucchetta.core.domain.league.LeagueEntryStatus;
import com.lucchetta.core.domain.league.LeagueInvitation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeagueInvitationRepository {

    void insert(LeagueInvitation invitation);

    Optional<LeagueInvitation> findById(UUID id);

    Optional<LeagueInvitation> findPending(UUID leagueId, UUID playerId);

    /** Pending invitations of the invitee, most recent first. */
    List<LeagueInvitation> findPendingByPlayer(UUID playerId);

    /**
     * Moves a pending invitation to its final status.
     *
     * @return false when the invitation was no longer pending
     */
    boolean decide(UUID id, LeagueEntryStatus status, Instant decidedAt);

    /** Cancels the user's pending invitations to the league, if any. */
    void cancelPending(UUID leagueId, UUID playerId, Instant at);
}
