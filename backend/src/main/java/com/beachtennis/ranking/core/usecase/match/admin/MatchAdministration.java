package com.beachtennis.ranking.core.usecase.match.admin;

import com.beachtennis.ranking.core.domain.AuthenticatedUser;
import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.domain.match.Justification;
import com.beachtennis.ranking.core.domain.match.Match;
import com.beachtennis.ranking.core.domain.match.MatchAuditEntry;
import com.beachtennis.ranking.core.domain.match.MatchStatus;
import com.beachtennis.ranking.core.domain.match.Participant;
import com.beachtennis.ranking.core.domain.match.Score;
import com.beachtennis.ranking.core.exception.ConflictException;
import com.beachtennis.ranking.core.exception.ForbiddenException;
import com.beachtennis.ranking.core.exception.NotFoundException;
import com.beachtennis.ranking.core.exception.RuleViolationException;
import com.beachtennis.ranking.core.port.MatchAuditLog;
import com.beachtennis.ranking.core.port.MatchRepository;
import com.beachtennis.ranking.core.port.TransactionRunner;
import com.beachtennis.ranking.core.usecase.match.MatchDetails;
import com.beachtennis.ranking.core.usecase.match.MatchDetailsAssembler;
import com.beachtennis.ranking.core.usecase.match.MatchValidator;
import com.beachtennis.ranking.core.usecase.player.GetOrCreateCurrentPlayer;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Administrator-only operations on registered matches: correction, void and void reversal.
 * Every change is audited with the state before and after. Points and statistics are derived
 * from the stored matches (D-007), so they follow automatically and are never applied twice.
 */
public class MatchAdministration {

    /**
     * @param expectedVersion version the administrator was looking at; protects against
     *                        overwriting a change made in between
     */
    public record Correction(UUID matchId, int expectedVersion, Instant playedAt, Score score,
                             List<Participant> participants, String reason) {
    }

    /**
     * @param before null when nothing changed (no-op correction)
     */
    public record Outcome(MatchDetails details, Match before, MatchAuditEntry.Action action) {

        public boolean changed() {
            return before != null;
        }
    }

    private final GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;
    private final MatchRepository matchRepository;
    private final MatchAuditLog auditLog;
    private final MatchValidator validator;
    private final MatchDetailsAssembler assembler;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public MatchAdministration(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                               MatchRepository matchRepository,
                               MatchAuditLog auditLog,
                               MatchValidator validator,
                               MatchDetailsAssembler assembler,
                               TransactionRunner transactionRunner,
                               Clock clock) {
        this.getOrCreateCurrentPlayer = getOrCreateCurrentPlayer;
        this.matchRepository = matchRepository;
        this.auditLog = auditLog;
        this.validator = validator;
        this.assembler = assembler;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    /**
     * Corrects date, format, score and participants. May change ranking eligibility (e.g. a guest
     * replaced by a registered player). Voided matches must be restored before being corrected.
     */
    public Outcome correct(AuthenticatedUser user, Correction correction) {
        String reason = Justification.optional(correction.reason());
        return change(user, correction.matchId(), MatchAuditEntry.Action.CORRECTED, reason, (current, now, admin) -> {
            if (current.status() == MatchStatus.VOIDED) {
                throw new RuleViolationException("MATCH_VOIDED");
            }
            if (current.version() != correction.expectedVersion()) {
                throw new ConflictException("MATCH_MODIFIED");
            }
            Match corrected = current.corrected(correction.playedAt(), correction.score(),
                    correction.participants(), now);
            if (corrected.hasSameContentAs(current)) {
                return current;
            }
            validator.validate(corrected, now);
            return corrected;
        });
    }

    /** Voids a match: it stays in the history but no longer counts for anything. */
    public Outcome voidMatch(AuthenticatedUser user, UUID matchId, String reason) {
        String justification = Justification.required(reason);
        return change(user, matchId, MatchAuditEntry.Action.VOIDED, justification,
                (current, now, admin) -> current.voided(justification, admin, now));
    }

    /** Restores a voided match; it counts again according to the current rules. */
    public Outcome revertVoid(AuthenticatedUser user, UUID matchId, String reason) {
        String justification = Justification.required(reason);
        return change(user, matchId, MatchAuditEntry.Action.VOID_REVERTED, justification,
                (current, now, admin) -> current.voidReverted(now));
    }

    private interface Change {
        /** Returns the new state, or {@code current} itself when nothing changes. */
        Match apply(Match current, Instant now, UUID adminId);
    }

    private Outcome change(AuthenticatedUser user, UUID matchId, MatchAuditEntry.Action action, String reason,
                           Change change) {
        if (!user.admin()) {
            throw new ForbiddenException();
        }
        Player admin = getOrCreateCurrentPlayer.execute(user);
        Instant now = clock.instant();
        return transactionRunner.inTransaction(() -> {
            Match current = matchRepository.findById(matchId)
                    .orElseThrow(() -> new NotFoundException("MATCH_NOT_FOUND"));
            Match next = change.apply(current, now, admin.id());
            if (next == current) {
                return new Outcome(assembler.assemble(current), null, action);
            }
            Match saved = matchRepository.update(next);
            auditLog.record(new MatchAuditEntry(matchId, action, admin.id(), now, reason, current, saved));
            return new Outcome(assembler.assemble(saved), current, action);
        });
    }
}
