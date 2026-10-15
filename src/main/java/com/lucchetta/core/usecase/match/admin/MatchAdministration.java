package com.lucchetta.core.usecase.match.admin;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.match.Justification;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchAuditEntry;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.domain.match.Score;
import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.MatchAuditLog;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.usecase.match.MatchDetails;
import com.lucchetta.core.usecase.match.MatchDetailsAssembler;
import com.lucchetta.core.usecase.match.MatchValidator;
import com.lucchetta.core.usecase.notification.MatchNotifier;
import com.lucchetta.core.usecase.notification.PushDispatcher;
import com.lucchetta.core.usecase.league.LeagueAccess;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * League administrator operations on a league's matches (decision D-018; the system administrator
 * may act in any league): correction, void and void reversal.
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

    private final LeagueAccess leagueAccess;
    private final MatchRepository matchRepository;
    private final MatchAuditLog auditLog;
    private final MatchValidator validator;
    private final MatchDetailsAssembler assembler;
    private final MatchNotifier notifier;
    private final PushDispatcher pushDispatcher;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public MatchAdministration(LeagueAccess leagueAccess,
                               MatchRepository matchRepository,
                               MatchAuditLog auditLog,
                               MatchValidator validator,
                               MatchDetailsAssembler assembler,
                               MatchNotifier notifier,
                               PushDispatcher pushDispatcher,
                               TransactionRunner transactionRunner,
                               Clock clock) {
        this.leagueAccess = leagueAccess;
        this.matchRepository = matchRepository;
        this.auditLog = auditLog;
        this.validator = validator;
        this.assembler = assembler;
        this.notifier = notifier;
        this.pushDispatcher = pushDispatcher;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    /**
     * Corrects date, format, score and participants. May change ranking eligibility (e.g. a guest
     * replaced by a registered player). Voided matches must be restored before being corrected.
     */
    public Outcome correct(AuthenticatedUser user, UUID leagueId, Correction correction) {
        String reason = Justification.optional(correction.reason());
        return change(user, leagueId, correction.matchId(), MatchAuditEntry.Action.CORRECTED, reason,
                (current, now, admin) -> {
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
    public Outcome voidMatch(AuthenticatedUser user, UUID leagueId, UUID matchId, String reason) {
        String justification = Justification.required(reason);
        return change(user, leagueId, matchId, MatchAuditEntry.Action.VOIDED, justification,
                (current, now, admin) -> current.voided(justification, admin, now));
    }

    /** Restores a voided match; it counts again according to the current rules. */
    public Outcome revertVoid(AuthenticatedUser user, UUID leagueId, UUID matchId, String reason) {
        String justification = Justification.required(reason);
        return change(user, leagueId, matchId, MatchAuditEntry.Action.VOID_REVERTED, justification,
                (current, now, admin) -> current.voidReverted(now));
    }

    private interface Change {
        /** Returns the new state, or {@code current} itself when nothing changes. */
        Match apply(Match current, Instant now, UUID adminId);
    }

    private Outcome change(AuthenticatedUser user, UUID leagueId, UUID matchId, MatchAuditEntry.Action action,
                           String reason, Change change) {
        LeagueAccess.Context context = leagueAccess.context(user, leagueId);
        LeagueAccess.requireAdministrator(context);
        LeagueAccess.requireNotArchived(context.league());
        Player admin = context.player();
        Instant now = clock.instant();
        record Result(Outcome outcome, List<Notification> notifications) {
        }
        Result result = transactionRunner.inTransaction(() -> {
            Match current = matchRepository.findById(matchId)
                    .orElseThrow(() -> new NotFoundException("MATCH_NOT_FOUND"));
            Match next = change.apply(current, now, admin.id());
            if (next == current) {
                return new Result(new Outcome(assembler.assemble(current), null, action), List.of());
            }
            Match saved = matchRepository.update(next);
            auditLog.record(new MatchAuditEntry(matchId, action, admin.id(), now, reason, current, saved));
            List<MatchDetails> details = assembler.assembleAll(List.of(current, saved));
            List<Notification> notifications = notifier.changed(action, details.get(0), details.get(1), reason);
            return new Result(new Outcome(details.get(1), current, action), notifications);
        });
        pushDispatcher.dispatch(result.notifications(), admin.id());
        return result.outcome();
    }
}
