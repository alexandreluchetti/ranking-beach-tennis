package com.lucchetta.core.usecase.tournament;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.tournament.StageEntrant;
import com.lucchetta.core.domain.tournament.TournamentStage;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.SeasonRepository;
import com.lucchetta.core.port.TournamentStageRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.usecase.league.LeagueAccess;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * League administrators open a stage on a playing day of a daily tournament season, with the
 * players present, and the server draws the pairs and groups (D-027). Until the first score the
 * draw may be redone (with new players or format) and the stage deleted; once the knockout is
 * drawn, the groups may be reopened while no knockout game has a score.
 */
public class ManageStages {

    /** A member ({@code playerId}) or a guest ({@code guestName}). */
    public record Attendee(UUID playerId, String guestName) {
    }

    /**
     * @param date    the playing day, or null for today
     * @param players the players present, in the order the administrator listed them
     */
    public record Setup(LocalDate date, MatchFormat format, List<Attendee> players) {
    }

    /** Null fields keep the current value. */
    public record Redraw(MatchFormat format, List<Attendee> players) {
    }

    private final LeagueAccess leagueAccess;
    private final SeasonRepository seasonRepository;
    private final LeagueMemberRepository memberRepository;
    private final TournamentStageRepository stageRepository;
    private final StageViewAssembler viewAssembler;
    private final TransactionRunner transactionRunner;
    private final RandomGenerator random;
    private final Clock clock;

    public ManageStages(LeagueAccess leagueAccess,
                        SeasonRepository seasonRepository,
                        LeagueMemberRepository memberRepository,
                        TournamentStageRepository stageRepository,
                        StageViewAssembler viewAssembler,
                        TransactionRunner transactionRunner,
                        RandomGenerator random,
                        Clock clock) {
        this.leagueAccess = leagueAccess;
        this.seasonRepository = seasonRepository;
        this.memberRepository = memberRepository;
        this.stageRepository = stageRepository;
        this.viewAssembler = viewAssembler;
        this.transactionRunner = transactionRunner;
        this.random = random;
        this.clock = clock;
    }

    public StageView create(AuthenticatedUser user, UUID leagueId, Setup setup) {
        LeagueAccess.Context context = administrator(user, leagueId);
        LocalDate today = BusinessCalendar.today(clock);
        LocalDate date = Objects.requireNonNullElse(setup.date(), today);
        if (date.isAfter(today)) {
            throw new RuleViolationException("STAGE_IN_FUTURE");
        }
        Season season = seasonRepository.findContaining(leagueId, date)
                .filter(Season::dailyTournament)
                .orElseThrow(() -> new RuleViolationException("STAGE_OUTSIDE_TOURNAMENT_SEASON"));
        if (!season.isPlayingDay(date)) {
            throw new RuleViolationException("STAGE_NOT_A_PLAYING_DAY");
        }
        List<StageEntrant> entrants = entrants(leagueId, setup.players());
        TournamentStage stage = TournamentStage.create(leagueId, season.id(), date, setup.format(), entrants,
                context.player().id(), random, clock.instant());

        TournamentStage created = transactionRunner.inTransaction(() -> {
            if (stageRepository.existsOn(season.id(), date)) {
                throw new ConflictException("STAGE_ALREADY_EXISTS");
            }
            return stageRepository.insert(stage);
        });
        return viewAssembler.view(created, context);
    }

    public StageView redraw(AuthenticatedUser user, UUID leagueId, UUID stageId, Redraw redraw) {
        LeagueAccess.Context context = administrator(user, leagueId);
        Instant now = clock.instant();
        TournamentStage saved = transactionRunner.inTransaction(() -> {
            TournamentStage current = QueryStages.requireInLeague(stageRepository, leagueId, stageId, true);
            List<StageEntrant> entrants = redraw.players() == null ? current.entrants()
                    : entrants(leagueId, redraw.players());
            return stageRepository.update(current.redrawn(entrants,
                    Objects.requireNonNullElse(redraw.format(), current.format()), random, now));
        });
        return viewAssembler.view(saved, context);
    }

    public StageView reopenGroups(AuthenticatedUser user, UUID leagueId, UUID stageId) {
        LeagueAccess.Context context = administrator(user, leagueId);
        Instant now = clock.instant();
        TournamentStage saved = transactionRunner.inTransaction(() -> stageRepository.update(
                QueryStages.requireInLeague(stageRepository, leagueId, stageId, true).groupsReopened(now)));
        return viewAssembler.view(saved, context);
    }

    public void delete(AuthenticatedUser user, UUID leagueId, UUID stageId) {
        administrator(user, leagueId);
        transactionRunner.inTransaction(() -> {
            QueryStages.requireInLeague(stageRepository, leagueId, stageId, true).requireNotStarted();
            stageRepository.delete(stageId);
            return null;
        });
    }

    private LeagueAccess.Context administrator(AuthenticatedUser user, UUID leagueId) {
        LeagueAccess.Context context = leagueAccess.context(user, leagueId);
        LeagueAccess.requireAdministrator(context);
        LeagueAccess.requireNotArchived(context.league());
        return context;
    }

    /** Members of the league from the list; anyone else as a guest by name (D-004). */
    private List<StageEntrant> entrants(UUID leagueId, List<Attendee> players) {
        if (players == null) {
            throw new RuleViolationException("INVALID_STAGE_PLAYER_COUNT");
        }
        List<StageEntrant> entrants = new ArrayList<>();
        for (Attendee player : players) {
            entrants.add(new StageEntrant(entrants.size() + 1, player.playerId(), player.guestName()));
        }
        Set<UUID> members = memberRepository.findByLeague(leagueId).stream()
                .map(LeagueMember::playerId)
                .collect(Collectors.toSet());
        if (entrants.stream().anyMatch(e -> !e.guest() && !members.contains(e.playerId()))) {
            throw new RuleViolationException("PLAYER_NOT_LEAGUE_MEMBER");
        }
        return entrants;
    }
}
