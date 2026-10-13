package com.lucchetta.core.port;

import com.lucchetta.core.domain.match.MatchAuditEntry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MatchAuditLog {

    void record(MatchAuditEntry entry);

    /** Audit trail of a match, oldest first. */
    List<Record> findByMatch(UUID matchId);

    /**
     * A stored audit entry. {@code before} and {@code after} are snapshots of the match as plain
     * values (date, format, score, tie-break, status, participants).
     */
    record Record(MatchAuditEntry.Action action, UUID performedBy, Instant performedAt, String reason,
                  Map<String, Object> before, Map<String, Object> after) {
    }
}
