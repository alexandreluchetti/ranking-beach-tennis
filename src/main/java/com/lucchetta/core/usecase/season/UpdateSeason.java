package com.lucchetta.core.usecase.season;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.domain.PlacementPoints;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.RankingType;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.SeasonAuditEntry;
import com.lucchetta.core.domain.SeasonStatus;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.SeasonAuditLog;
import com.lucchetta.core.port.SeasonRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.usecase.league.LeagueAccess;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Changes a season of a league. League administrators (or the system administrator) only.
 * <ul>
 *     <li>Closed seasons cannot be changed or reopened (D-006).</li>
 *     <li>Active seasons keep their start date; the end date may be extended or brought forward,
 *     but not before today.</li>
 *     <li>Active seasons may change their playing days; the change applies from today on, so
 *     matches of earlier days keep counting (or not) as they did (D-025).</li>
 *     <li>Planned seasons may change name, both dates, the playing days and the ranking type with
 *     its placement points; the ranking is locked once the season starts (D-026).</li>
 * </ul>
 */
public class UpdateSeason {

    /**
     * Null fields are left unchanged. Changing to a daily tournament without a table keeps the
     * current table, or takes the default one (D-028).
     */
    public record Command(String name, LocalDate startDate, LocalDate endDate, Set<DayOfWeek> playingDays,
                          RankingType rankingType, PlacementPoints placementPoints) {

        public Command(String name, LocalDate startDate, LocalDate endDate, Set<DayOfWeek> playingDays) {
            this(name, startDate, endDate, playingDays, null, null);
        }
    }

    private final LeagueAccess leagueAccess;
    private final SeasonRepository seasonRepository;
    private final SeasonAuditLog auditLog;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public UpdateSeason(LeagueAccess leagueAccess,
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

    public SeasonView execute(AuthenticatedUser user, UUID leagueId, UUID seasonId, Command command) {
        LeagueAccess.Context context = leagueAccess.context(user, leagueId);
        LeagueAccess.requireAdministrator(context);
        LeagueAccess.requireNotArchived(context.league());
        Player admin = context.player();
        LocalDate today = BusinessCalendar.today(clock);
        Instant now = clock.instant();

        Season result = transactionRunner.inTransaction(() -> {
            Season current = QuerySeasons.requireInLeague(seasonRepository, leagueId, seasonId);
            SeasonStatus status = current.statusOn(today);
            if (status == SeasonStatus.CLOSED) {
                throw new RuleViolationException("SEASON_CLOSED");
            }

            String name = Objects.requireNonNullElse(command.name(), current.name());
            LocalDate start = Objects.requireNonNullElse(command.startDate(), current.startDate());
            LocalDate end = Objects.requireNonNullElse(command.endDate(), current.endDate());
            Set<DayOfWeek> playingDays = Objects.requireNonNullElse(command.playingDays(), current.playingDays());
            if (status == SeasonStatus.ACTIVE && !start.equals(current.startDate())) {
                throw new RuleViolationException("SEASON_START_LOCKED");
            }
            if (end.isBefore(today)) {
                throw new RuleViolationException("SEASON_END_IN_PAST");
            }
            RankingType rankingType = Objects.requireNonNullElse(command.rankingType(), current.rankingType());
            PlacementPoints placementPoints = CreateSeason.placementPoints(rankingType,
                    command.placementPoints() == null && rankingType == current.rankingType()
                            ? current.placementPoints()
                            : command.placementPoints());
            if (status != SeasonStatus.PLANNED && (rankingType != current.rankingType()
                    || !Objects.equals(placementPoints, current.placementPoints()))) {
                throw new RuleViolationException("SEASON_RANKING_LOCKED");
            }

            // Planned: the new days replace the old ones. Active: they apply from today on (D-025).
            LocalDate playingDaysFrom = status == SeasonStatus.PLANNED ? start : today;
            Season updated = current.withChanges(name, start, end, now)
                    .withPlayingDays(playingDays, playingDaysFrom, now)
                    .withRanking(rankingType, placementPoints, now);
            if (sameContent(current, updated)) {
                return current;
            }
            if (seasonRepository.existsOverlapping(leagueId, updated.startDate(), updated.endDate(), updated.id())) {
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
                && a.endDate().equals(b.endDate())
                && a.playingDays().equals(b.playingDays())
                && a.pastPlayingDays().equals(b.pastPlayingDays())
                && a.rankingType() == b.rankingType()
                && Objects.equals(a.placementPoints(), b.placementPoints());
    }
}
