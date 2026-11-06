package com.lucchetta.core.usecase.tournament;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.domain.match.Justification;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchAuditEntry;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.domain.match.Score;
import com.lucchetta.core.domain.match.Team;
import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.domain.tournament.StageEntrant;
import com.lucchetta.core.domain.tournament.StageFixture;
import com.lucchetta.core.domain.tournament.StagePair;
import com.lucchetta.core.domain.tournament.TournamentStage;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.ForbiddenException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.MatchAuditLog;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.port.TournamentStageRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.match.MatchDetails;
import com.lucchetta.core.usecase.match.MatchValidator;
import com.lucchetta.core.usecase.match.MatchDetailsAssembler;
import com.lucchetta.core.usecase.notification.MatchNotifier;
import com.lucchetta.core.usecase.notification.PushDispatcher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scores of the games of a stage (D-027). Each score is stored as a match of the league, linked to
 * the stage, so it shows in the history and the statistics and notifies its players like any
 * match. Who enters a first score follows the league setting (D-029); changing or clearing a score
 * is for league administrators, and is audited like a match correction or void. After every
 * change the stage draws the next round when the current one is complete.
 */
public class StageScores {

    /**
     * @param expectedMatchVersion version of the match being corrected, as the administrator saw
     *                             it; null skips the check
     */
    public record Entry(int teamAGames, int teamBGames, Integer tiebreakTeamAPoints, Integer tiebreakTeamBPoints,
                        Integer expectedMatchVersion, String reason) {
    }

    private final LeagueAccess leagueAccess;
    private final TournamentStageRepository stageRepository;
    private final MatchRepository matchRepository;
    private final MatchAuditLog auditLog;
    private final MatchValidator validator;
    private final MatchDetailsAssembler matchDetailsAssembler;
    private final StageViewAssembler viewAssembler;
    private final MatchNotifier notifier;
    private final PushDispatcher pushDispatcher;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public StageScores(LeagueAccess leagueAccess,
                       TournamentStageRepository stageRepository,
                       MatchRepository matchRepository,
                       MatchAuditLog auditLog,
                       MatchValidator validator,
                       MatchDetailsAssembler matchDetailsAssembler,
                       StageViewAssembler viewAssembler,
                       MatchNotifier notifier,
                       PushDispatcher pushDispatcher,
                       TransactionRunner transactionRunner,
                       Clock clock) {
        this.leagueAccess = leagueAccess;
        this.stageRepository = stageRepository;
        this.matchRepository = matchRepository;
        this.auditLog = auditLog;
        this.validator = validator;
        this.matchDetailsAssembler = matchDetailsAssembler;
        this.viewAssembler = viewAssembler;
        this.notifier = notifier;
        this.pushDispatcher = pushDispatcher;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    /** Enters the score of a game, or corrects it (league administrators) when it already has one. */
    public StageView record(AuthenticatedUser user, UUID leagueId, UUID stageId, int fixtureNumber, Entry entry) {
        LeagueAccess.Context context = leagueAccess.context(user, leagueId);
        LeagueAccess.requireNotArchived(context.league());
        if (!context.canAdminister() && !(context.member() && context.league().allowsScoreEntryBy(false))) {
            throw context.member() ? new ForbiddenException("SCORE_ENTRY_ADMINS_ONLY") : new ForbiddenException();
        }
        UUID actor = context.player().id();
        Instant now = clock.instant();
        record Outcome(TournamentStage stage, List<Notification> notifications) {
        }
        Outcome outcome = transactionRunner.inTransaction(() -> {
            TournamentStage locked = QueryStages.requireInLeague(stageRepository, leagueId, stageId, true);
            TournamentStage stage = locked.openForScore(fixtureNumber, now);
            StageFixture fixture = stage.fixture(fixtureNumber).orElseThrow();
            Score score = new Score(stage.format(), entry.teamAGames(), entry.teamBGames(),
                    entry.tiebreakTeamAPoints(), entry.tiebreakTeamBPoints());
            List<Notification> notifications;
            if (fixture.scored()) {
                if (!context.canAdminister()) {
                    throw new ForbiddenException("STAGE_SCORE_ALREADY_ENTERED");
                }
                Match current = matchRepository.findById(fixture.matchId()).orElseThrow();
                if (entry.expectedMatchVersion() != null && current.version() != entry.expectedMatchVersion()) {
                    throw new ConflictException("MATCH_MODIFIED");
                }
                Match corrected = current.corrected(current.playedAt(), score, current.participants(), now);
                if (corrected.hasSameContentAs(current)) {
                    return new Outcome(locked, List.of());
                }
                Match saved = matchRepository.update(corrected);
                String reason = Justification.optional(entry.reason());
                auditLog.record(new MatchAuditEntry(current.id(), MatchAuditEntry.Action.CORRECTED, actor, now, reason,
                        current, saved));
                List<MatchDetails> details = matchDetailsAssembler.assembleAll(List.of(current, saved));
                notifications = notifier.changed(MatchAuditEntry.Action.CORRECTED, details.get(0), details.get(1),
                        reason);
            } else {
                Match match = new Match(UUID.randomUUID(), leagueId, playedAt(stage, now), score,
                        participants(stage, fixture), MatchStatus.VALID, actor, now, now, null, 0, stage.id());
                validator.validate(match, now);
                matchRepository.insertIfAbsent(match);
                stage = stage.withFixtureMatch(fixtureNumber, match.id(), now);
                notifications = notifier.registered(matchDetailsAssembler.assemble(match));
            }
            TournamentStage advanced = stage.advanced(viewAssembler.results(stage), now);
            return new Outcome(stageRepository.update(advanced), notifications);
        });
        pushDispatcher.dispatch(outcome.notifications(), actor);
        return viewAssembler.view(outcome.stage(), context);
    }

    /**
     * Removes the score of a game (league administrators): its match is voided with the reason
     * and the game waits for a new score. Only while no later round has a score; a group game
     * only before the knockout is drawn (D-027).
     */
    public StageView clear(AuthenticatedUser user, UUID leagueId, UUID stageId, int fixtureNumber, String reason) {
        String justification = Justification.required(reason);
        LeagueAccess.Context context = leagueAccess.context(user, leagueId);
        LeagueAccess.requireAdministrator(context);
        LeagueAccess.requireNotArchived(context.league());
        UUID actor = context.player().id();
        Instant now = clock.instant();
        record Outcome(TournamentStage stage, List<Notification> notifications) {
        }
        Outcome outcome = transactionRunner.inTransaction(() -> {
            TournamentStage stage = QueryStages.requireInLeague(stageRepository, leagueId, stageId, true)
                    .openForScore(fixtureNumber, now);
            StageFixture fixture = stage.fixture(fixtureNumber).orElseThrow();
            if (!fixture.scored()) {
                throw new RuleViolationException("STAGE_FIXTURE_NOT_SCORED");
            }
            Match current = matchRepository.findById(fixture.matchId()).orElseThrow();
            Match saved = matchRepository.update(current.voided(justification, actor, now));
            auditLog.record(new MatchAuditEntry(current.id(), MatchAuditEntry.Action.VOIDED, actor, now,
                    justification, current, saved));
            List<MatchDetails> details = matchDetailsAssembler.assembleAll(List.of(current, saved));
            List<Notification> notifications = notifier.changed(MatchAuditEntry.Action.VOIDED, details.get(0),
                    details.get(1), justification);
            return new Outcome(stageRepository.update(stage.withFixtureMatch(fixtureNumber, null, now)),
                    notifications);
        });
        pushDispatcher.dispatch(outcome.notifications(), actor);
        return viewAssembler.view(outcome.stage(), context);
    }

    /** The moment of entering the score on the stage day; noon (Brasília) when entered on a later day. */
    private static Instant playedAt(TournamentStage stage, Instant now) {
        return stage.date().equals(LocalDate.ofInstant(now, BusinessCalendar.ZONE))
                ? now
                : stage.date().atTime(LocalTime.NOON).atZone(BusinessCalendar.ZONE).toInstant();
    }

    /** Pair A plays as team A. */
    private static List<Participant> participants(TournamentStage stage, StageFixture fixture) {
        List<Participant> participants = new ArrayList<>(4);
        addPair(participants, stage, Team.A, stage.pair(fixture.pairA()));
        addPair(participants, stage, Team.B, stage.pair(fixture.pairB()));
        return participants;
    }

    private static void addPair(List<Participant> participants, TournamentStage stage, Team team, StagePair pair) {
        addEntrant(participants, team, 1, stage.entrant(pair.firstPosition()));
        addEntrant(participants, team, 2, stage.entrant(pair.secondPosition()));
    }

    private static void addEntrant(List<Participant> participants, Team team, int slot, StageEntrant entrant) {
        participants.add(entrant.guest() ? Participant.guest(team, slot, entrant.guestName())
                : Participant.player(team, slot, entrant.playerId()));
    }
}
