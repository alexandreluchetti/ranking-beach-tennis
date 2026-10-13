package com.lucchetta.core.domain.match;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Record of an administrative operation on a match, with the full state before and after.
 *
 * @param reason mandatory for voids and void reversals, optional for corrections
 */
public record MatchAuditEntry(
        UUID matchId,
        Action action,
        UUID performedBy,
        Instant performedAt,
        String reason,
        Match before,
        Match after) {

    public enum Action {
        CORRECTED,
        VOIDED,
        VOID_REVERTED
    }

    public MatchAuditEntry {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(performedBy, "performedBy");
        Objects.requireNonNull(performedAt, "performedAt");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }
}
