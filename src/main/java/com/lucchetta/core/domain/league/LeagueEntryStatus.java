package com.lucchetta.core.domain.league;

/** State of a join request or an invitation (decision D-019). */
public enum LeagueEntryStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    /** Withdrawn by the requester, or made moot because the user joined the league another way. */
    CANCELLED
}
