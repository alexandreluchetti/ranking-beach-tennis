package com.lucchetta.core.usecase.season;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.SeasonAuditEntry;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.SeasonAuditLog;
import com.lucchetta.core.port.SeasonRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.usecase.league.LeagueAccess;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Creates a season of a league. League administrators (or the system administrator) only;
 * seasons are never created automatically.
 */
public class CreateSeason {

    public record Command(String name, LocalDate startDate, LocalDate endDate) {
    }

    private final LeagueAccess leagueAccess;
    private final SeasonRepository seasonRepository;
    private final SeasonAuditLog auditLog;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public CreateSeason(LeagueAccess leagueAccess,
                        SeasonRepository seasonRepository,
                        SeasonAuditLog auditLog,
                        TransactionRunner transactionRunner,
                        Clock clock) {
        this.leagueAccess = leagueAccess;
        this.seasonRepository = seasonRepository;
        this.auditLog = auditLog;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public SeasonView execute(AuthenticatedUser user, UUID leagueId, Command command) {
        LeagueAccess.Context context = leagueAccess.context(user, leagueId);
        LeagueAccess.requireAdministrator(context);
        LeagueAccess.requireNotArchived(context.league());
        Player admin = context.player();
        LocalDate today = BusinessCalendar.today(clock);
        Instant now = clock.instant();
        Season season = new Season(
                UUID.randomUUID(), leagueId, command.name(), command.startDate(), command.endDate(), admin.id(), now,
                now);
        if (season.endDate().isBefore(today)) {
            throw new RuleViolationException("SEASON_END_IN_PAST");
        }

        Season created = transactionRunner.inTransaction(() -> {
            if (seasonRepository.existsOverlapping(leagueId, season.startDate(), season.endDate(), null)) {
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
