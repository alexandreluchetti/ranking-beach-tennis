package com.lucchetta.core.domain.match;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Who voided a match, when and why. Present only while the match is voided; the full history
 * stays in the audit log.
 */
public record VoidInfo(String reason, UUID voidedBy, Instant voidedAt) {

    public VoidInfo {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(voidedBy, "voidedBy");
        Objects.requireNonNull(voidedAt, "voidedAt");
    }
}
