package com.beachtennis.ranking.core.support;

import com.beachtennis.ranking.core.domain.match.MatchAuditEntry;
import com.beachtennis.ranking.core.port.MatchAuditLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InMemoryMatchAudit implements MatchAuditLog {

    private final List<MatchAuditEntry> entries = new ArrayList<>();

    public List<MatchAuditEntry> entries() {
        return entries;
    }

    @Override
    public void record(MatchAuditEntry entry) {
        entries.add(entry);
    }

    @Override
    public List<Record> findByMatch(UUID matchId) {
        return entries.stream()
                .filter(e -> e.matchId().equals(matchId))
                .map(e -> new Record(e.action(), e.performedBy(), e.performedAt(), e.reason(),
                        Map.of("version", e.before().version()), Map.of("version", e.after().version())))
                .toList();
    }
}
