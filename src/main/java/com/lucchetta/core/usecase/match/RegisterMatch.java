package com.lucchetta.core.usecase.match;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.domain.match.Score;
import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.ForbiddenException;
import com.lucchetta.core.exception.PossibleDuplicateMatchException;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.usecase.notification.MatchNotifier;
import com.lucchetta.core.usecase.notification.PushDispatcher;
import com.lucchetta.core.usecase.league.LeagueAccess;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Registers a finished match in a league. Any member of the league may do it (decision D-021);
 * every registered player must be a member too, anyone else takes part as a guest.
 * <ul>
 *     <li>Idempotent by client-generated id: resending returns the stored match without creating
 *     anything. Different content under the same id is a conflict, unless the administrator has
 *     changed the stored match meanwhile (then the resend just returns its current state).</li>
 *     <li>Matches more than 10 minutes in the future are rejected (D-011).</li>
 *     <li>A same-day match with the same teams requires explicit confirmation (D-012).</li>
 * </ul>
 */
public class RegisterMatch {

    public record Command(
            UUID id,
            Instant playedAt,
            Score score,
            List<Participant> participants,
            boolean confirmPossibleDuplicate) {
    }

    /** @param created false when the request was an idempotent resend */
    public record Result(MatchDetails details, boolean created) {
    }

    private final LeagueAccess leagueAccess;
    private final MatchRepository matchRepository;
    private final MatchValidator validator;
    private final MatchDetailsAssembler assembler;
    private final MatchNotifier notifier;
    private final PushDispatcher pushDispatcher;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public RegisterMatch(LeagueAccess leagueAccess,
                         MatchRepository matchRepository,
                         MatchValidator validator,
                         MatchDetailsAssembler assembler,
                         MatchNotifier notifier,
                         PushDispatcher pushDispatcher,
                         TransactionRunner transactionRunner,
                         Clock clock) {
        this.leagueAccess = leagueAccess;
        this.matchRepository = matchRepository;
        this.validator = validator;
        this.assembler = assembler;
        this.notifier = notifier;
        this.pushDispatcher = pushDispatcher;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public Result execute(AuthenticatedUser user, UUID leagueId, Command command) {
        Objects.requireNonNull(command.id(), "id");
        LeagueAccess.Context context = leagueAccess.context(user, leagueId);
        if (!context.member()) {
            throw new ForbiddenException();
        }
        LeagueAccess.requireNotArchived(context.league());
        Player registeredBy = context.player();
        Instant now = clock.instant();
        Match match = new Match(command.id(), leagueId, command.playedAt(), command.score(), command.participants(),
                MatchStatus.VALID, registeredBy.id(), now, now);

        record Outcome(Result result, List<Notification> notifications) {
        }
        Outcome outcome = transactionRunner.inTransaction(() -> {
            Match existing = matchRepository.findById(match.id()).orElse(null);
            if (existing != null) {
                return new Outcome(idempotentResend(existing, match), List.of());
            }
            validator.validate(match, now);
            if (!command.confirmPossibleDuplicate()) {
                rejectPossibleDuplicates(match);
            }
            if (!matchRepository.insertIfAbsent(match)) {
                // Lost a race against a concurrent resend of the same id.
                return new Outcome(idempotentResend(matchRepository.findById(match.id()).orElseThrow(), match),
                        List.of());
            }
            MatchDetails details = assembler.assemble(match);
            return new Outcome(new Result(details, true), notifier.registered(details));
        });
        pushDispatcher.dispatch(outcome.notifications(), registeredBy.id());
        return outcome.result();
    }

    private Result idempotentResend(Match existing, Match requested) {
        boolean changedByAdministrator = existing.version() > 0;
        if (!existing.leagueId().equals(requested.leagueId())
                || (!changedByAdministrator && !existing.hasSameContentAs(requested))) {
            throw new ConflictException("MATCH_ID_CONFLICT");
        }
        return new Result(assembler.assemble(existing), false);
    }

    private void rejectPossibleDuplicates(Match match) {
        LocalDate day = match.playedOn();
        Instant from = day.atStartOfDay(BusinessCalendar.ZONE).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(BusinessCalendar.ZONE).toInstant();
        List<UUID> duplicates = matchRepository.findValidPlayedBetween(match.leagueId(), from, to).stream()
                .filter(match::hasSameTeamsAs)
                .map(Match::id)
                .toList();
        if (!duplicates.isEmpty()) {
            throw new PossibleDuplicateMatchException(duplicates);
        }
    }
}
