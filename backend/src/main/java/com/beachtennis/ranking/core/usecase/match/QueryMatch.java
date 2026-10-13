package com.beachtennis.ranking.core.usecase.match;

import com.beachtennis.ranking.core.exception.NotFoundException;
import com.beachtennis.ranking.core.port.MatchAuditLog;
import com.beachtennis.ranking.core.port.MatchRepository;

import java.util.List;
import java.util.UUID;

public class QueryMatch {

    private final MatchRepository matchRepository;
    private final MatchAuditLog auditLog;
    private final MatchDetailsAssembler assembler;

    public QueryMatch(MatchRepository matchRepository, MatchAuditLog auditLog, MatchDetailsAssembler assembler) {
        this.matchRepository = matchRepository;
        this.auditLog = auditLog;
        this.assembler = assembler;
    }

    public MatchDetails byId(UUID id) {
        return matchRepository.findById(id)
                .map(assembler::assemble)
                .orElseThrow(() -> new NotFoundException("MATCH_NOT_FOUND"));
    }

    /** Administrative history of a match (corrections, voids, reversals), oldest first. */
    public List<MatchAuditLog.Record> auditTrail(UUID id) {
        if (matchRepository.findById(id).isEmpty()) {
            throw new NotFoundException("MATCH_NOT_FOUND");
        }
        return auditLog.findByMatch(id);
    }
}
