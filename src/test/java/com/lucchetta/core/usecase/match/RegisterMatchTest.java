package com.lucchetta.core.usecase.match;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.domain.league.LeagueVisibility;
import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.domain.match.Score;
import com.lucchetta.core.domain.match.Team;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.ForbiddenException;
import com.lucchetta.core.exception.PossibleDuplicateMatchException;
import com.lucchetta.core.scoring.GamesPlusWinBonusScoringPolicy;
import com.lucchetta.core.scoring.NotCountedReason;
import com.lucchetta.core.scoring.RankingEligibilityPolicy;
import com.lucchetta.core.support.DirectTransactionRunner;
import com.lucchetta.core.support.InMemoryMatches;
import com.lucchetta.core.support.InMemoryPlayers;
import com.lucchetta.core.support.InMemorySeasons;
import com.lucchetta.core.support.TestLeague;
import com.lucchetta.core.support.InMemoryNotifications;
import com.lucchetta.core.domain.notification.DevicePlatform;
import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.usecase.notification.MatchNotificationComposer;
import com.lucchetta.core.usecase.notification.MatchNotifier;
import com.lucchetta.core.usecase.notification.PushDispatcher;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterMatchTest {

    /** 12:00 in Brasília on Sunday 2026-10-11. */
    private static final Instant NOW = Instant.parse("2026-10-11T15:00:00Z");
    private static final Instant PLAYED = NOW.minus(Duration.ofHours(1));

    private final InMemoryPlayers players = new InMemoryPlayers();
    private final InMemorySeasons seasons = new InMemorySeasons();
    private final InMemoryMatches matches = new InMemoryMatches();
    private final InMemoryNotifications notifications = new InMemoryNotifications();
    private final TestLeague league = new TestLeague();
    private RegisterMatch useCase;
    private Player ana;
    private Player beto;
    private Player caio;
    private Player dani;
    private AuthenticatedUser caller;

    @BeforeEach
    void setUp() {
        ana = players.add("Ana");
        beto = players.add("Beto");
        caio = players.add("Caio");
        dani = players.add("Dani");
        league.members(ana, beto, caio, dani);
        caller = new AuthenticatedUser(ana.identity(), null, "Ana", false);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        MatchDetailsAssembler assembler = new MatchDetailsAssembler(seasons, players, new RankingEligibilityPolicy(),
                new GamesPlusWinBonusScoringPolicy());
        MatchNotifier notifier = new MatchNotifier(notifications, league.leagues,
                new MatchNotificationComposer(), clock);
        useCase = new RegisterMatch(league.access(new GetOrCreateCurrentPlayer(players, clock)), matches,
                new MatchValidator(league.leagues), assembler, notifier,
                new PushDispatcher(notifications, notifications),
                new DirectTransactionRunner(), clock);
    }

    @Test
    void registrationNotifiesRegisteredParticipantsOnceAndPushesToOthersOnly() {
        notifications.save("ana-phone", ana.id(), DevicePlatform.ANDROID, NOW);
        notifications.save("caio-phone", caio.id(), DevicePlatform.IOS, NOW);
        List<Participant> withGuest = List.of(
                Participant.player(Team.A, 1, ana.id()), Participant.player(Team.A, 2, beto.id()),
                Participant.player(Team.B, 1, caio.id()), Participant.guest(Team.B, 2, null));
        UUID id = UUID.randomUUID();

        register(caller, command(id, sixFour(), withGuest));
        register(caller, command(id, sixFour(), withGuest));

        assertThat(notifications.all()).extracting(Notification::recipientId)
                .containsExactlyInAnyOrder(ana.id(), beto.id(), caio.id());
        assertThat(notifications.pushedTokens()).containsExactly("caio-phone");
    }

    @Test
    void registersMatchStoringScoreAndAwardingPointsInsideSeason() {
        Season season = activeSeason();

        RegisterMatch.Result result = register(caller, command(UUID.randomUUID(), sixFour(), regularTeams()));

        MatchDetails details = result.details();
        assertThat(result.created()).isTrue();
        assertThat(matches.count()).isEqualTo(1);
        assertThat(details.match().score()).isEqualTo(sixFour());
        assertThat(details.match().winner()).isEqualTo(Team.A);
        assertThat(details.match().status()).isEqualTo(MatchStatus.VALID);
        assertThat(details.match().registeredBy()).isEqualTo(ana.id());
        assertThat(details.season()).isEqualTo(season);
        assertThat(details.eligibility().counted()).isTrue();
        assertThat(details.pointsFor(Team.A)).isEqualTo(9);
        assertThat(details.pointsFor(Team.B)).isEqualTo(4);
        assertThat(details.playerNames()).containsEntry(caio.id(), "Caio");
    }

    @Test
    void guestMatchGivesPointsToTheRegisteredPlayers() {
        activeSeason();
        List<Participant> withGuest = List.of(
                Participant.player(Team.A, 1, ana.id()), Participant.player(Team.A, 2, beto.id()),
                Participant.player(Team.B, 1, caio.id()), Participant.guest(Team.B, 2, "Zé"));

        MatchDetails details = register(caller, command(UUID.randomUUID(), sixFour(), withGuest)).details();

        assertThat(matches.count()).isEqualTo(1);
        assertThat(details.eligibility().counted()).isTrue();
        assertThat(details.pointsFor(Team.A)).isEqualTo(9);
        assertThat(details.pointsFor(Team.B)).isEqualTo(4);
    }

    @Test
    void matchWithoutSeasonIsStoredForHistoryOnly() {
        MatchDetails details = register(caller, command(UUID.randomUUID(), sixFour(), regularTeams())).details();

        assertThat(matches.count()).isEqualTo(1);
        assertThat(details.season()).isNull();
        assertThat(details.eligibility().reason()).isEqualTo(NotCountedReason.NO_SEASON);
        assertThat(details.pointsFor(Team.A)).isZero();
    }

    @Test
    void resendingTheSameMatchIsIdempotent() {
        activeSeason();
        UUID id = UUID.randomUUID();
        register(caller, command(id, sixFour(), regularTeams()));

        RegisterMatch.Result resend = register(caller, command(id, sixFour(), regularTeams()));

        assertThat(resend.created()).isFalse();
        assertThat(matches.count()).isEqualTo(1);
        assertThat(matches.insertCalls()).isEqualTo(1);
        assertThat(resend.details().pointsFor(Team.A)).isEqualTo(9);
    }

    @Test
    void resendIsIdempotentEvenIfAnIdenticalMatchWouldLookLikeADuplicate() {
        UUID id = UUID.randomUUID();
        register(caller, command(id, sixFour(), regularTeams()));

        assertThat(register(caller, command(id, sixFour(), regularTeams())).created()).isFalse();
    }

    @Test
    void sameIdWithDifferentContentIsAConflict() {
        UUID id = UUID.randomUUID();
        register(caller, command(id, sixFour(), regularTeams()));

        assertThatThrownBy(() -> register(caller,
                command(id, new Score(MatchFormat.STANDARD, 6, 3, null, null), regularTeams())))
                .isInstanceOf(ConflictException.class)
                .hasMessage("MATCH_ID_CONFLICT");
        assertThat(matches.count()).isEqualTo(1);
    }

    @Test
    void lateResendAfterAnAdministrativeChangeReturnsTheCurrentState() {
        UUID id = UUID.randomUUID();
        register(caller, command(id, sixFour(), regularTeams()));
        var stored = matches.findById(id).orElseThrow();
        matches.update(stored.corrected(stored.playedAt(), new Score(MatchFormat.STANDARD, 6, 2, null, null),
                stored.participants(), NOW));

        RegisterMatch.Result resend = register(caller, command(id, sixFour(), regularTeams()));

        assertThat(resend.created()).isFalse();
        assertThat(resend.details().match().score().teamBGames()).isEqualTo(2);
        assertThat(matches.count()).isEqualTo(1);
    }

    @Test
    void sameTeamsSameDayRequireConfirmation() {
        UUID first = UUID.randomUUID();
        register(caller, command(first, sixFour(), regularTeams()));
        List<Participant> swappedSides = List.of(
                Participant.player(Team.A, 1, dani.id()), Participant.player(Team.A, 2, caio.id()),
                Participant.player(Team.B, 1, beto.id()), Participant.player(Team.B, 2, ana.id()));

        assertThatThrownBy(() -> register(caller, command(UUID.randomUUID(), sixFour(), swappedSides)))
                .isInstanceOfSatisfying(PossibleDuplicateMatchException.class,
                        e -> assertThat(e.existingMatchIds()).containsExactly(first));
        assertThat(matches.count()).isEqualTo(1);
    }

    @Test
    void confirmedDuplicateIsRegistered() {
        register(caller, command(UUID.randomUUID(), sixFour(), regularTeams()));

        RegisterMatch.Result second = register(caller, new RegisterMatch.Command(
                UUID.randomUUID(), PLAYED.plusSeconds(3600), sixFour(), regularTeams(), true));

        assertThat(second.created()).isTrue();
        assertThat(matches.count()).isEqualTo(2);
    }

    @Test
    void differentPairsOrDifferentDayAreNotDuplicates() {
        register(caller, command(UUID.randomUUID(), sixFour(), regularTeams()));
        List<Participant> newPairs = List.of(
                Participant.player(Team.A, 1, ana.id()), Participant.player(Team.A, 2, caio.id()),
                Participant.player(Team.B, 1, beto.id()), Participant.player(Team.B, 2, dani.id()));

        register(caller, command(UUID.randomUUID(), sixFour(), newPairs));
        register(caller, new RegisterMatch.Command(UUID.randomUUID(), PLAYED.minus(Duration.ofDays(7)),
                sixFour(), regularTeams(), false));

        assertThat(matches.count()).isEqualTo(3);
    }

    @Test
    void withoutATimeTheMatchIsPlayedWhenTheServerReceivesIt() {
        UUID id = UUID.randomUUID();

        RegisterMatch.Result first = register(caller, new RegisterMatch.Command(id, null, sixFour(), regularTeams(),
                false));
        RegisterMatch.Result resend = register(caller, new RegisterMatch.Command(id, null, sixFour(), regularTeams(),
                false));

        assertThat(first.details().match().playedAt()).isEqualTo(NOW);
        assertThat(resend.created()).isFalse(); // no time compared on a resend without one
        assertThat(matches.count()).isEqualTo(1);
    }

    @Test
    void aPastDateWithTheTimeOfRegisteringIsKept() {
        Instant yesterdayAtRegistration = NOW.minus(Duration.ofDays(1));

        MatchDetails details = register(caller, new RegisterMatch.Command(UUID.randomUUID(), yesterdayAtRegistration,
                sixFour(), regularTeams(), false)).details();

        assertThat(details.match().playedAt()).isEqualTo(yesterdayAtRegistration);
        assertThat(details.match().registeredAt()).isEqualTo(NOW);
    }

    @Test
    void futureMatchesAreRejectedBeyondTenMinutes() {
        RegisterMatch.Command tooLate = new RegisterMatch.Command(UUID.randomUUID(),
                NOW.plus(Duration.ofMinutes(10)).plusSeconds(1), sixFour(), regularTeams(), false);
        RegisterMatch.Command withinTolerance = new RegisterMatch.Command(UUID.randomUUID(),
                NOW.plus(Duration.ofMinutes(10)), sixFour(), regularTeams(), false);

        assertThatThrownBy(() -> register(caller, tooLate)).hasMessage("MATCH_IN_FUTURE");
        assertThat(register(caller, withinTolerance).created()).isTrue();
    }

    @Test
    void playersWhoAreNotMembersAreRejected() {
        List<Participant> stranger = List.of(
                Participant.player(Team.A, 1, ana.id()), Participant.player(Team.A, 2, UUID.randomUUID()),
                Participant.player(Team.B, 1, caio.id()), Participant.player(Team.B, 2, dani.id()));

        assertThatThrownBy(() -> register(caller, command(UUID.randomUUID(), sixFour(), stranger)))
                .hasMessage("PLAYER_NOT_LEAGUE_MEMBER");
        assertThat(matches.count()).isZero();
    }

    @Test
    void callerDoesNotNeedToBeAParticipant() {
        Player eva = players.add("Eva");
        league.members(eva);
        AuthenticatedUser evaCaller = new AuthenticatedUser(eva.identity(), null, "Eva", false);

        MatchDetails details = register(evaCaller, command(UUID.randomUUID(), sixFour(), regularTeams())).details();

        assertThat(details.match().registeredBy()).isEqualTo(eva.id());
    }

    @Test
    void onlyMembersRegisterMatchesInTheLeague() {
        Player outsider = players.add("Fora");
        AuthenticatedUser outsiderCaller = new AuthenticatedUser(outsider.identity(), null, "Fora", false);

        assertThatThrownBy(() -> register(outsiderCaller, command(UUID.randomUUID(), sixFour(), regularTeams())))
                .isInstanceOf(ForbiddenException.class);
        assertThat(matches.count()).isZero();
    }

    @Test
    void leagueAdministratorsAreNotifiedOfEveryMatch() {
        Player eva = players.add("Eva");
        league.administrator(eva);

        UUID id = UUID.randomUUID();
        register(caller, command(id, sixFour(), regularTeams()));

        assertThat(notifications.all()).extracting(Notification::recipientId)
                .containsExactlyInAnyOrder(ana.id(), beto.id(), caio.id(), dani.id(), eva.id());
        assertThat(notifications.of(eva.id()).getFirst().leagueId()).isEqualTo(TestLeague.ID);
        assertThat(notifications.of(eva.id()).getFirst().matchId()).isEqualTo(id);
    }

    @Test
    void matchIdOfAnotherLeagueIsAConflict() {
        UUID id = UUID.randomUUID();
        register(caller, command(id, sixFour(), regularTeams()));
        UUID otherLeague = UUID.randomUUID();
        league.leagues.add(new League(otherLeague, "Outra Liga", null,
                LeagueVisibility.PUBLIC, null, null, NOW, NOW, null, null, 0));
        league.leagues.insert(new LeagueMember(otherLeague, ana.id(),
                LeagueRole.ADMIN, NOW));

        assertThatThrownBy(() -> useCase.execute(caller, otherLeague, command(id, sixFour(), List.of(
                Participant.player(Team.A, 1, ana.id()), Participant.guest(Team.A, 2, null),
                Participant.guest(Team.B, 1, null), Participant.guest(Team.B, 2, null)))))
                .hasMessage("MATCH_ID_CONFLICT");
    }

    private RegisterMatch.Result register(AuthenticatedUser user, RegisterMatch.Command command) {
        return useCase.execute(user, TestLeague.ID, command);
    }

    private Season activeSeason() {
        Instant created = Instant.parse("2026-09-01T12:00:00Z");
        return seasons.add(new Season(UUID.randomUUID(), TestLeague.ID, "Primavera", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31), ana.id(), created, created));
    }

    private List<Participant> regularTeams() {
        return List.of(
                Participant.player(Team.A, 1, ana.id()), Participant.player(Team.A, 2, beto.id()),
                Participant.player(Team.B, 1, caio.id()), Participant.player(Team.B, 2, dani.id()));
    }

    private static Score sixFour() {
        return new Score(MatchFormat.STANDARD, 6, 4, null, null);
    }

    private static RegisterMatch.Command command(UUID id, Score score, List<Participant> participants) {
        return new RegisterMatch.Command(id, PLAYED, score, participants, false);
    }
}
