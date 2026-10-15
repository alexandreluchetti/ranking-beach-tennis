package com.lucchetta.core.port;

import com.lucchetta.core.domain.league.JoinRequest;
import com.lucchetta.core.domain.league.LeagueEntryStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JoinRequestRepository {

    void insert(JoinRequest request);

    Optional<JoinRequest> findById(UUID id);

    Optional<JoinRequest> findPending(UUID leagueId, UUID playerId);

    /** Pending requests of a league, oldest first. */
    List<JoinRequest> findPendingByLeague(UUID leagueId);

    List<JoinRequest> findPendingByPlayer(UUID playerId);

    /**
     * Moves a pending request to its final status.
     *
     * @return false when the request was no longer pending
     */
    boolean decide(UUID id, LeagueEntryStatus status, UUID decidedBy, Instant decidedAt);

    /** Cancels the user's pending request to the league, if any. */
    void cancelPending(UUID leagueId, UUID playerId, Instant at);
}
