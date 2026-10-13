package com.lucchetta.core.usecase.season;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.SeasonAuditEntry;
import com.lucchetta.core.domain.SeasonStatus;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.ForbiddenException;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.SeasonAuditLog;
import com.lucchetta.core.port.SeasonRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Changes a season. Administrator only.
 * <ul>
 *     <li>Closed seasons cannot be changed or reopened (D-006).</li>
 *     <li>Active seasons keep their start date; the end date may be extended or brought forward,
 *     but not before today.</li>
 *     <li>Planned seasons may change name and both dates.</li>
 * </ul>
 */
public class UpdateSeason {

    /** Null fields are left unchanged. */
    public record Command(String name, LocalDate startDate, LocalDate endDate) {
    }

    private final GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;
    private final SeasonRepository seasonRepository;
    private final SeasonAuditLog auditLog;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public UpdateSeason(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                        SeasonRepository seasonRepository,
                        SeasonAuditLog auditLog,
                        TransactionRunner transactionRunner,
                        Clock clock) {
        this.getOrCreateCurrentPlayer = getOrCreateCurrentPlayer;
        this.seasonRepository = seasonRepository;
        this.auditLog = auditLog;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public SeasonView execute(AuthenticatedUser user, UUID seasonId, Command command) {
        if (!user.admin()) {
            throw new ForbiddenException();
        }
        Player admin = getOrCreateCurrentPlayer.execute(user);
        LocalDate today = BusinessCalendar.today(clock);
        Instant now = clock.instant();

        Season result = transactionRunner.inTransaction(() -> {
            Season current = seasonRepository.findById(seasonId)
                    .orElseThrow(() -> new NotFoundException("SEASON_NOT_FOUND"));
            SeasonStatus status = current.statusOn(today);
            if (status == SeasonStatus.CLOSED) {
                throw new RuleViolationException("SEASON_CLOSED");
            }

            String name = Objects.requireNonNullElse(command.name(), current.name());
            LocalDate start = Objects.requireNonNullElse(command.startDate(), current.startDate());
            LocalDate end = Objects.requireNonNullElse(command.endDate(), current.endDate());
            if (status == SeasonStatus.ACTIVE && !start.equals(current.startDate())) {
                throw new RuleViolationException("SEASON_START_LOCKED");
            }
            if (end.isBefore(today)) {
                throw new RuleViolationException("SEASON_END_IN_PAST");
            }

            Season updated = current.withChanges(name, start, end, now);
            if (sameContent(current, updated)) {
                return current;
            }
            if (seasonRepository.existsOverlapping(updated.startDate(), updated.endDate(), updated.id())) {
                throw new ConflictException("SEASON_OVERLAP");
            }
            Season saved = seasonRepository.update(updated);
            auditLog.record(new SeasonAuditEntry(
                    saved.id(), SeasonAuditEntry.Action.UPDATED, admin.id(), now, current, saved));
            return saved;
        });
        return new SeasonView(result, result.statusOn(today));
    }

    private static boolean sameContent(Season a, Season b) {
        return a.name().equals(b.name())
                && a.startDate().equals(b.startDate())
                && a.endDate().equals(b.endDate());
    }
}
