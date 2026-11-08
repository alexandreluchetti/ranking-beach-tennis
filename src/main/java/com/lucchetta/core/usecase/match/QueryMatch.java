package com.lucchetta.core.usecase.match;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.port.MatchAuditLog;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.usecase.league.LeagueAccess;

import java.util.List;
import java.util.UUID;

/**
 * One match of a league and its audit trail, for the league's members (decision D-020). A match of
 * another league that counts in this one (D-031) is shown as it counts here.
 */
public class QueryMatch {

    private final LeagueAccess leagueAccess;
    private final MatchRepository matchRepository;
    private final MatchAuditLog auditLog;
    private final MatchDetailsAssembler assembler;
    private final SharedMatches sharedMatches;

    public QueryMatch(LeagueAccess leagueAccess, MatchRepository matchRepository, MatchAuditLog auditLog,
                      MatchDetailsAssembler assembler, SharedMatches sharedMatches) {
        this.leagueAccess = leagueAccess;
        this.matchRepository = matchRepository;
        this.auditLog = auditLog;
        this.assembler = assembler;
        this.sharedMatches = sharedMatches;
    }

    public MatchDetails byId(AuthenticatedUser user, UUID leagueId, UUID id) {
        leagueAccess.requireMember(user, leagueId);
        return find(leagueId, id);
    }

    /** Administrative history of a match (corrections, voids, reversals), oldest first. */
    public List<MatchAuditLog.Record> auditTrail(AuthenticatedUser user, UUID leagueId, UUID id) {
        leagueAccess.requireMember(user, leagueId);
        find(leagueId, id);
        return auditLog.findByMatch(id);
    }

    /** A match of another league that does not count in this one is reported as not found. */
    private MatchDetails find(UUID leagueId, UUID id) {
        Match match = matchRepository.findById(id).orElseThrow(() -> new NotFoundException("MATCH_NOT_FOUND"));
        if (match.leagueId().equals(leagueId)) {
            return assembler.assemble(match);
        }
        return sharedMatches.sharedInto(leagueId, match)
                .orElseThrow(() -> new NotFoundException("MATCH_NOT_FOUND"));
    }
}
