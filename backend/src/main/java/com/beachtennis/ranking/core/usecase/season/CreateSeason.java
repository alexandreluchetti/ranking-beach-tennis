package com.beachtennis.ranking.core.usecase.season;

import com.beachtennis.ranking.core.domain.AuthenticatedUser;
import com.beachtennis.ranking.core.domain.BusinessCalendar;
import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.domain.Season;
import com.beachtennis.ranking.core.domain.SeasonAuditEntry;
import com.beachtennis.ranking.core.exception.ConflictException;
import com.beachtennis.ranking.core.exception.ForbiddenException;
import com.beachtennis.ranking.core.exception.RuleViolationException;
import com.beachtennis.ranking.core.port.SeasonAuditLog;
import com.beachtennis.ranking.core.port.SeasonRepository;
import com.beachtennis.ranking.core.port.TransactionRunner;
import com.beachtennis.ranking.core.usecase.player.GetOrCreateCurrentPlayer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Creates a season. Administrator only. Seasons are never created automatically.
 */
public class CreateSeason {

    public record Command(String name, LocalDate startDate, LocalDate endDate) {
    }

    private final GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;
    private final SeasonRepository seasonRepository;
    private final SeasonAuditLog auditLog;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public CreateSeason(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
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

    public SeasonView execute(AuthenticatedUser user, Command command) {
        if (!user.admin()) {
            throw new ForbiddenException();
        }
        Player admin = getOrCreateCurrentPlayer.execute(user);
        LocalDate today = BusinessCalendar.today(clock);
        Instant now = clock.instant();
        Season season = new Season(
                UUID.randomUUID(), command.name(), command.startDate(), command.endDate(), admin.id(), now, now);
        if (season.endDate().isBefore(today)) {
            throw new RuleViolationException("SEASON_END_IN_PAST");
        }

        Season created = transactionRunner.inTransaction(() -> {
            if (seasonRepository.existsOverlapping(season.startDate(), season.endDate(), null)) {
                throw new ConflictException("SEASON_OVERLAP");
            }
            Season inserted = seasonRepository.insert(season);
            auditLog.record(new SeasonAuditEntry(
                    inserted.id(), SeasonAuditEntry.Action.CREATED, admin.id(), now, null, inserted));
            return inserted;
        });
        return new SeasonView(created, created.statusOn(today));
    }
}
