package com.lucchetta.core.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Record of an administrative change to a season.
 *
 * @param before state before the change; null on creation
 * @param after  state after the change
 */
public record SeasonAuditEntry(
        UUID seasonId,
        Action action,
        UUID performedBy,
        Instant performedAt,
        Season before,
        Season after) {

    public enum Action {
        CREATED,
        UPDATED
    }
}
