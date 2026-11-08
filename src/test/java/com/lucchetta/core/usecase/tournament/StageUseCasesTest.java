package com.lucchetta.core.usecase.tournament;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.PlacementPoints;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.RankingType;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.ScoreEntry;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchAuditEntry;
import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.tournament.Placement;
import com.lucchetta.core.domain.tournament.StageFixture;
import com.lucchetta.core.domain.tournament.StagePair;
import com.lucchetta.core.domain.tournament.StageRound;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.ForbiddenException;
import com.lucchetta.core.port.LeagueMatchCache;
import com.lucchetta.core.scoring.GamesPlusWinBonusScoringPolicy;
import com.lucchetta.core.scoring.RankingEligibilityPolicy;
import com.lucchetta.core.stats.RankingEntry;
import com.lucchetta.core.stats.TournamentRecord;
import com.lucchetta.core.support.DirectTransactionRunner;
import com.lucchetta.core.support.InMemoryMatchAudit;
import com.lucchetta.core.support.InMemoryMatches;
import com.lucchetta.core.support.InMemoryNotifications;
import com.lucchetta.core.support.InMemoryPlayers;
import com.lucchetta.core.support.InMemorySeasons;
import com.lucchetta.core.support.InMemoryStages;
import com.lucchetta.core.support.TestLeague;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.match.LeagueMatches;
import com.lucchetta.core.usecase.match.MatchDetails;
import com.lucchetta.core.usecase.match.MatchDetailsAssembler;
import com.lucchetta.core.usecase.match.MatchValidator;
import com.lucchetta.core.usecase.match.SharedMatches;
import com.lucchetta.core.usecase.match.admin.MatchAdministration;
import com.lucchetta.core.usecase.notification.MatchNotificationComposer;
import com.lucchetta.core.usecase.notification.MatchNotifier;
import com.lucchetta.core.usecase.notification.PushDispatcher;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;
import com.lucchetta.core.usecase.ranking.GetPlayerProfile;
import com.lucchetta.core.usecase.ranking.GetSeasonRanking;
import com.lucchetta.core.usecase.ranking.SeasonStanding;
import com.lucchetta.core.usecase.season.QuerySeasons;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opening, drawing and scoring a daily tournament stage (D-027). */
class StageUseCasesTest {

    /** 19:00 in Brasília on Tuesday 2026-11-03. */
    private static final Instant NOW = Instant.parse("2026-11-03T22:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryPlayers players = new InMemoryPlayers();
    private final InMemorySeasons seasons = new InMemorySeasons();
    private final InMemoryMatches matches = new InMemoryMatches();
    private final InMemoryStages stages = new InMemoryStages(matches);
    private final InMemoryMatchAudit audit = new InMemoryMatchAudit();
    private final InMemoryNotifications notifications = new InMemoryNotifications();
    private final TestLeague league = new TestLeague();
    private final LeagueAccess access = league.access(new GetOrCreateCurrentPlayer(players, CLOCK));
    private final MatchDetailsAssembler assembler = new MatchDetailsAssembler(seasons, players,
            new RankingEligibilityPolicy(), new GamesPlusWinBonusScoringPolicy());
    private final MatchNotifier notifier = new MatchNotifier(notifications, league.leagues,
            new MatchNotificationComposer(), CLOCK);
    private final PushDispatcher push = new PushDispatcher(notifications, notifications);
    private final StageViewAssembler views = new StageViewAssembler(matches, assembler, players);
    private final ManageStages manage = new ManageStages(access, seasons, league.leagues, stages, views,
            new DirectTransactionRunner(), new Random(3), CLOCK);
    private final StageScores scores = new StageScores(access, stages, matches, audit,
            new MatchValidator(league.leagues), assembler, views, notifier, push, new DirectTransactionRunner(), CLOCK);
    private final QueryStages query = new QueryStages(access, new QuerySeasons(access, seasons, CLOCK), stages, views);

    private final Player admin = players.add("Admin");
    private final List<Player> members = new ArrayList<>();

    {
        league.administrator(admin);
        for (String name : List.of("Ana", "Beto", "Caio", "Dani", "Edu", "Fabi")) {
            Player member = players.add(name);
            league.members(member);
            members.add(member);
        }
    }

    /** Tuesdays from 2026-10-01 to 2026-12-31. */
    private final Season tournament = seasons.add(new Season(UUID.randomUUID(), TestLeague.ID, "Torneio",
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), EnumSet.of(DayOfWeek.TUESDAY), List.of(),
            RankingType.DAILY_TOURNAMENT, PlacementPoints.DEFAULT, admin.id(), NOW, NOW));

    @Test
    void administratorOpensTheStageOfTheDayWithMembersAndGuests() {
        StageView view = open(null);

        assertThat(view.stage().date()).isEqualTo(TODAY);
        assertThat(view.stage().seasonId()).isEqualTo(tournament.id());
        assertThat(view.stage().entrants()).hasSize(8);
        assertThat(view.stage().pairs()).hasSize(4);
        assertThat(view.stage().fixtures()).hasSize(6);
        assertThat(view.playerNames()).containsValues("Admin", "Ana", "Fabi");
        assertThat(view.canAdminister()).isTrue();
        assertThat(query.list(user(members.getFirst()), TestLeague.ID, null)).extracting(s -> s.id())
                .containsExactly(view.stage().id());
        assertThat(query.get(user(members.getFirst()), TestLeague.ID, view.stage().id()).canAdminister()).isFalse();
    }

    @Test
    void stageNeedsAPlayingDayOfADailyTournamentSeason() {
        assertThatThrownBy(() -> manage.create(user(members.getFirst()), TestLeague.ID, setup(null)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> open(TODAY.plusDays(7))).hasMessage("STAGE_IN_FUTURE");
        assertThatThrownBy(() -> open(TODAY.minusDays(1))).hasMessage("STAGE_NOT_A_PLAYING_DAY");
        assertThatThrownBy(() -> open(LocalDate.of(2026, 9, 29))).hasMessage("STAGE_OUTSIDE_TOURNAMENT_SEASON");
        open(null);
        assertThatThrownBy(() -> open(null)).isInstanceOf(ConflictException.class).hasMessage("STAGE_ALREADY_EXISTS");

        Player outsider = players.add("Gil");
        List<ManageStages.Attendee> withOutsider = new ArrayList<>(attendees());
        withOutsider.set(0, new ManageStages.Attendee(outsider.id(), null));
        assertThatThrownBy(() -> manage.create(user(admin), TestLeague.ID,
                new ManageStages.Setup(TODAY.minusDays(7), MatchFormat.STANDARD, withOutsider)))
                .hasMessage("PLAYER_NOT_LEAGUE_MEMBER");
    }

    @Test
    void scoresAreMatchesOfTheStageAndTheKnockoutFollows() {
        StageView view = open(null);
        UUID stageId = view.stage().id();

        for (StageFixture fixture : view.stage().fixtures()) {
            view = scores.record(user(members.getFirst()), TestLeague.ID, stageId, fixture.number(), entry(6, 3));
        }
        assertThat(view.stage().knockoutDrawn()).isTrue();
        StageFixture fin = view.stage().fixtures().getLast();
        assertThat(fin.round()).isEqualTo(StageRound.FINAL);
        assertThat(view.placements()).isEmpty();

        view = scores.record(user(members.getFirst()), TestLeague.ID, stageId, fin.number(), entry(2, 6));

        assertThat(view.stage().finished()).isTrue();
        assertThat(view.placements()).containsEntry(fin.pairB(), Placement.CHAMPION)
                .containsEntry(fin.pairA(), Placement.RUNNER_UP);
        assertThat(matches.findByStage(stageId)).hasSize(7);
        MatchDetails final_ = view.matches().get(fin.number());
        assertThat(final_.match().stageId()).isEqualTo(stageId);
        assertThat(final_.match().playedAt()).isEqualTo(NOW);
        assertThat(final_.match().format()).isEqualTo(MatchFormat.STANDARD);
        assertThat(final_.eligibility().counted()).isTrue();
        assertThat(final_.pointsFor(com.lucchetta.core.domain.match.Team.A)).isZero();
        assertThat(notifications.all()).isNotEmpty();
    }

    @Test
    void onlyAdministratorsCorrectAScoreAndTheCorrectionIsAudited() {
        StageView view = open(null);
        UUID stageId = view.stage().id();
        view = scores.record(user(members.getFirst()), TestLeague.ID, stageId, 1, entry(6, 3));
        Match entered = view.matches().get(1).match();

        assertThatThrownBy(() -> scores.record(user(members.getFirst()), TestLeague.ID, stageId, 1, entry(6, 2)))
                .isInstanceOf(ForbiddenException.class).hasMessage("STAGE_SCORE_ALREADY_ENTERED");
        assertThatThrownBy(() -> scores.record(user(admin), TestLeague.ID, stageId, 1,
                new StageScores.Entry(6, 2, null, null, entered.version() + 1, null)))
                .hasMessage("MATCH_MODIFIED");

        view = scores.record(user(admin), TestLeague.ID, stageId, 1,
                new StageScores.Entry(6, 2, null, null, entered.version(), "Placar anotado errado"));

        assertThat(view.results().get(1).pairBGames()).isEqualTo(2);
        assertThat(view.matches().get(1).match().id()).isEqualTo(entered.id());
        assertThat(audit.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(MatchAuditEntry.Action.CORRECTED);
            assertThat(entry.reason()).isEqualTo("Placar anotado errado");
        });
    }

    @Test
    void leagueMayLeaveScoresToItsAdministrators() {
        League current = league.leagues.findById(TestLeague.ID).orElseThrow();
        league.leagues.update(current.withDetails(current.name(), current.description(), current.visibility(),
                ScoreEntry.ADMINS, NOW));
        StageView view = open(null);

        assertThat(query.get(user(members.getFirst()), TestLeague.ID, view.stage().id()).canEnterScores()).isFalse();
        assertThatThrownBy(() -> scores.record(user(members.getFirst()), TestLeague.ID, view.stage().id(), 1,
                entry(6, 3))).hasMessage("SCORE_ENTRY_ADMINS_ONLY");
        assertThat(scores.record(user(admin), TestLeague.ID, view.stage().id(), 1, entry(6, 3)).results())
                .containsKey(1);
    }

    @Test
    void clearingAScoreVoidsItsMatchAndTheStageCannotBeDrawnAgainOrDeleted() {
        StageView view = open(null);
        UUID stageId = view.stage().id();
        view = scores.record(user(admin), TestLeague.ID, stageId, 1, entry(6, 3));
        UUID matchId = view.matches().get(1).match().id();

        assertThatThrownBy(() -> scores.clear(user(admin), TestLeague.ID, stageId, 1, " "))
                .hasMessage("REASON_REQUIRED");
        view = scores.clear(user(admin), TestLeague.ID, stageId, 1, "Dupla errada");

        assertThat(view.results()).isEmpty();
        assertThat(view.stage().fixture(1).orElseThrow().scored()).isFalse();
        assertThat(matches.findById(matchId).orElseThrow().status()).isEqualTo(MatchStatus.VOIDED);
        assertThat(audit.entries().getLast().action()).isEqualTo(MatchAuditEntry.Action.VOIDED);
        assertThatThrownBy(() -> scores.clear(user(admin), TestLeague.ID, stageId, 1, "de novo"))
                .hasMessage("STAGE_FIXTURE_NOT_SCORED");
        assertThatThrownBy(() -> manage.delete(user(admin), TestLeague.ID, stageId)).hasMessage("STAGE_STARTED");
    }

    @Test
    void drawIsRedoneOrTheStageDeletedBeforeTheFirstScore() {
        StageView view = open(null);
        UUID stageId = view.stage().id();
        List<ManageStages.Attendee> ten = new ArrayList<>(attendees());
        ten.add(new ManageStages.Attendee(null, "Convidado 3"));
        ten.add(new ManageStages.Attendee(null, "Convidado 4"));

        view = manage.redraw(user(admin), TestLeague.ID, stageId, new ManageStages.Redraw(MatchFormat.SHORT, ten));
        assertThat(view.stage().pairs()).hasSize(5);
        assertThat(view.stage().format()).isEqualTo(MatchFormat.SHORT);

        scores.record(user(admin), TestLeague.ID, stageId, 1, entry(4, 2));
        assertThatThrownBy(() -> manage.redraw(user(admin), TestLeague.ID, stageId,
                new ManageStages.Redraw(null, null))).hasMessage("STAGE_STARTED");

        StageView other = open(TODAY.minusDays(7));
        manage.delete(user(admin), TestLeague.ID, other.stage().id());
        assertThat(stages.findById(other.stage().id())).isEmpty();
    }

    @Test
    void aScoreEnteredOnALaterDayKeepsTheStageDay() {
        StageView view = open(TODAY.minusDays(7));

        view = scores.record(user(admin), TestLeague.ID, view.stage().id(), 1, entry(6, 1));

        assertThat(view.matches().get(1).match().playedAt())
                .isEqualTo(TODAY.minusDays(7).atTime(LocalTime.NOON).atZone(java.time.ZoneId.of("America/Sao_Paulo"))
                        .toInstant());
        assertThat(view.matches().get(1).season().id()).isEqualTo(tournament.id());
    }

    @Test
    void stageMatchesAreNotChangedThroughTheMatchEndpoints() {
        StageView view = open(null);
        view = scores.record(user(admin), TestLeague.ID, view.stage().id(), 1, entry(6, 3));
        UUID matchId = view.matches().get(1).match().id();
        MatchAdministration administration = new MatchAdministration(access, matches, audit,
                new MatchValidator(league.leagues), assembler, notifier, push, new DirectTransactionRunner(), CLOCK);

        assertThatThrownBy(() -> administration.voidMatch(user(admin), TestLeague.ID, matchId, "teste"))
                .hasMessage("STAGE_MATCH");
    }

    @Test
    void groupsAreReopenedByAnAdministrator() {
        StageView view = open(null);
        UUID stageId = view.stage().id();
        for (StageFixture fixture : view.stage().fixtures()) {
            view = scores.record(user(admin), TestLeague.ID, stageId, fixture.number(), entry(6, 3));
        }
        assertThatThrownBy(() -> scores.record(user(admin), TestLeague.ID, stageId, 1, entry(6, 0)))
                .hasMessage("STAGE_GROUPS_CLOSED");
        assertThatThrownBy(() -> manage.reopenGroups(user(members.getFirst()), TestLeague.ID, stageId))
                .isInstanceOf(ForbiddenException.class);

        view = manage.reopenGroups(user(admin), TestLeague.ID, stageId);
        assertThat(view.stage().knockoutDrawn()).isFalse();

        view = scores.record(user(admin), TestLeague.ID, stageId, 1, entry(6, 0));
        assertThat(view.stage().knockoutDrawn()).isTrue();
        assertThat(view.results().get(1).pairBGames()).isZero();
    }

    @Test
    void seasonRankingSumsThePlacementPointsOfFinishedStages() {
        Player late = players.add("Gil");
        league.members(late);
        StageView first = playStage(TODAY.minusDays(7));
        StageView unfinished = open(null);
        scores.record(user(admin), TestLeague.ID, unfinished.stage().id(), 1, entry(6, 3));

        List<RankingEntry> ranking = ranking().current(user(admin), TestLeague.ID).orElseThrow().entries();

        Map<UUID, Integer> expected = new HashMap<>();
        first.placements().forEach((pair, placement) -> {
            StagePair p = first.stage().pair(pair);
            for (int position : List.of(p.firstPosition(), p.secondPosition())) {
                UUID player = first.stage().entrant(position).playerId();
                if (player != null) {
                    expected.put(player, PlacementPoints.DEFAULT.pointsFor(placement));
                }
            }
        });
        assertThat(ranking).hasSize(8); // admin, six members and Gil; never the guest
        assertThat(ranking).allSatisfy(e -> {
            assertThat(e.stats().points()).isEqualTo(expected.getOrDefault(e.playerId(), 0));
            assertThat(e.tournament()).isNotNull();
        });
        RankingEntry leader = ranking.getFirst();
        assertThat(leader.stats().points()).isEqualTo(100);
        assertThat(leader.tournament()).isEqualTo(new TournamentRecord(1, 1, 1));
        assertThat(leader.stats().matchesPlayed()).isPositive(); // statistics still come from the stage matches
        RankingEntry gil = ranking.stream().filter(e -> e.playerId().equals(late.id())).findFirst().orElseThrow();
        assertThat(gil.tournament()).isEqualTo(TournamentRecord.EMPTY);
        assertThat(ranking.getLast().stats().points()).isZero();

        SeasonStanding standing = profiles().execute(user(admin), TestLeague.ID, leader.playerId()).currentSeason();
        assertThat(standing.position()).isEqualTo(1);
        assertThat(standing.tournament()).isEqualTo(new TournamentRecord(1, 1, 1));
    }

    @Test
    void clearingTheFinalRemovesThePlacementPoints() {
        StageView played = playStage(null);
        StageFixture fin = played.stage().fixtures().getLast();

        scores.clear(user(admin), TestLeague.ID, played.stage().id(), fin.number(), "Final anotada errada");

        assertThat(ranking().current(user(admin), TestLeague.ID).orElseThrow().entries())
                .allSatisfy(e -> assertThat(e.stats().points()).isZero());
    }

    private StageView playStage(LocalDate date) {
        StageView view = open(date);
        while (!view.stage().finished()) {
            StageFixture next = view.stage().fixtures().stream().filter(f -> !f.scored()).findFirst().orElseThrow();
            view = scores.record(user(admin), TestLeague.ID, view.stage().id(), next.number(), entry(6, 3));
        }
        return view;
    }

    private GetSeasonRanking ranking() {
        return new GetSeasonRanking(access, new QuerySeasons(access, seasons, CLOCK), players, league.leagues,
                leagueMatches(), stages);
    }

    private GetPlayerProfile profiles() {
        return new GetPlayerProfile(access, players, league.leagues, new QuerySeasons(access, seasons, CLOCK),
                leagueMatches(), stages);
    }

    private LeagueMatches leagueMatches() {
        return new LeagueMatches(matches, assembler,
                new SharedMatches(league.leagues, league.leagues, matches, assembler), LeagueMatchCache.NONE);
    }

    private StageView open(LocalDate date) {
        return manage.create(user(admin), TestLeague.ID, setup(date));
    }

    private ManageStages.Setup setup(LocalDate date) {
        return new ManageStages.Setup(date, MatchFormat.STANDARD, attendees());
    }

    /** The administrator, six members and a guest. */
    private List<ManageStages.Attendee> attendees() {
        List<ManageStages.Attendee> attendees = new ArrayList<>();
        attendees.add(new ManageStages.Attendee(admin.id(), null));
        members.forEach(m -> attendees.add(new ManageStages.Attendee(m.id(), null)));
        attendees.add(new ManageStages.Attendee(null, "Convidado"));
        return attendees;
    }

    private static StageScores.Entry entry(int teamAGames, int teamBGames) {
        return new StageScores.Entry(teamAGames, teamBGames, null, null, null, null);
    }

    private static AuthenticatedUser user(Player player) {
        return new AuthenticatedUser(player.identity(), null, player.displayName().value(), false);
    }
}
