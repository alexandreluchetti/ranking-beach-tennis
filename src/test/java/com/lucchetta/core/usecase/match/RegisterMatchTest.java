package com.lucchetta.core.usecase.match;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.domain.match.Score;
import com.lucchetta.core.domain.match.Team;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.PossibleDuplicateMatchException;
import com.lucchetta.core.scoring.GamesPlusWinBonusScoringPolicy;
import com.lucchetta.core.scoring.NotCountedReason;
import com.lucchetta.core.scoring.RankingEligibilityPolicy;
import com.lucchetta.core.support.DirectTransactionRunner;
import com.lucchetta.core.support.InMemoryMatches;
import com.lucchetta.core.support.InMemoryPlayers;
import com.lucchetta.core.support.InMemorySeasons;
import com.lucchetta.core.support.FixedAdministratorDirectory;
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
        caller = new AuthenticatedUser(ana.identity(), null, "Ana", false);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        MatchDetailsAssembler assembler = new MatchDetailsAssembler(seasons, players, new RankingEligibilityPolicy(),
                new GamesPlusWinBonusScoringPolicy());
        MatchNotifier notifier = new MatchNotifier(notifications, new FixedAdministratorDirectory(null),
                new MatchNotificationComposer(), clock);
        useCase = new RegisterMatch(new GetOrCreateCurrentPlayer(players, clock), matches,
                new MatchValidator(players), assembler, notifier, new PushDispatcher(notifications, notifications),
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

        useCase.execute(caller, command(id, sixFour(), withGuest));
        useCase.execute(caller, command(id, sixFour(), withGuest));

        assertThat(notifications.all()).extracting(Notification::recipientId)
                .containsExactlyInAnyOrder(ana.id(), beto.id(), caio.id());
        assertThat(notifications.pushedTokens()).containsExactly("caio-phone");
    }

    @Test
    void registersMatchStoringScoreAndAwardingPointsInsideSeason() {
        Season season = activeSeason();

        RegisterMatch.Result result = useCase.execute(caller, command(UUID.randomUUID(), sixFour(), regularTeams()));

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
    void guestMatchIsStoredButGivesNoPoints() {
        activeSeason();
        List<Participant> withGuest = List.of(
                Participant.player(Team.A, 1, ana.id()), Participant.player(Team.A, 2, beto.id()),
                Participant.player(Team.B, 1, caio.id()), Participant.guest(Team.B, 2, "Zé"));

        MatchDetails details = useCase.execute(caller, command(UUID.randomUUID(), sixFour(), withGuest)).details();

        assertThat(matches.count()).isEqualTo(1);
        assertThat(details.eligibility().counted()).isFalse();
        assertThat(details.eligibility().reason()).isEqualTo(NotCountedReason.GUEST_PRESENT);
        assertThat(details.pointsFor(Team.A)).isZero();
        assertThat(details.pointsFor(Team.B)).isZero();
    }

    @Test
    void matchWithoutSeasonIsStoredForHistoryOnly() {
        MatchDetails details = useCase.execute(caller, command(UUID.randomUUID(), sixFour(), regularTeams())).details();

        assertThat(matches.count()).isEqualTo(1);
        assertThat(details.season()).isNull();
        assertThat(details.eligibility().reason()).isEqualTo(NotCountedReason.NO_SEASON);
        assertThat(details.pointsFor(Team.A)).isZero();
    }

    @Test
    void resendingTheSameMatchIsIdempotent() {
        activeSeason();
        UUID id = UUID.randomUUID();
        useCase.execute(caller, command(id, sixFour(), regularTeams()));

        RegisterMatch.Result resend = useCase.execute(caller, command(id, sixFour(), regularTeams()));

        assertThat(resend.created()).isFalse();
        assertThat(matches.count()).isEqualTo(1);
        assertThat(matches.insertCalls()).isEqualTo(1);
        assertThat(resend.details().pointsFor(Team.A)).isEqualTo(9);
    }

    @Test
    void resendIsIdempotentEvenIfAnIdenticalMatchWouldLookLikeADuplicate() {
        UUID id = UUID.randomUUID();
        useCase.execute(caller, command(id, sixFour(), regularTeams()));

        assertThat(useCase.execute(caller, command(id, sixFour(), regularTeams())).created()).isFalse();
    }

    @Test
    void sameIdWithDifferentContentIsAConflict() {
        UUID id = UUID.randomUUID();
        useCase.execute(caller, command(id, sixFour(), regularTeams()));

        assertThatThrownBy(() -> useCase.execute(caller,
                command(id, new Score(MatchFormat.STANDARD, 6, 3, null, null), regularTeams())))
                .isInstanceOf(ConflictException.class)
                .hasMessage("MATCH_ID_CONFLICT");
        assertThat(matches.count()).isEqualTo(1);
    }

    @Test
    void lateResendAfterAnAdministrativeChangeReturnsTheCurrentState() {
        UUID id = UUID.randomUUID();
        useCase.execute(caller, command(id, sixFour(), regularTeams()));
        var stored = matches.findById(id).orElseThrow();
        matches.update(stored.corrected(stored.playedAt(), new Score(MatchFormat.STANDARD, 6, 2, null, null),
                stored.participants(), NOW));

        RegisterMatch.Result resend = useCase.execute(caller, command(id, sixFour(), regularTeams()));

        assertThat(resend.created()).isFalse();
        assertThat(resend.details().match().score().teamBGames()).isEqualTo(2);
        assertThat(matches.count()).isEqualTo(1);
    }

    @Test
    void sameTeamsSameDayRequireConfirmation() {
        UUID first = UUID.randomUUID();
        useCase.execute(caller, command(first, sixFour(), regularTeams()));
        List<Participant> swappedSides = List.of(
                Participant.player(Team.A, 1, dani.id()), Participant.player(Team.A, 2, caio.id()),
                Participant.player(Team.B, 1, beto.id()), Participant.player(Team.B, 2, ana.id()));

        assertThatThrownBy(() -> useCase.execute(caller, command(UUID.randomUUID(), sixFour(), swappedSides)))
                .isInstanceOfSatisfying(PossibleDuplicateMatchException.class,
                        e -> assertThat(e.existingMatchIds()).containsExactly(first));
        assertThat(matches.count()).isEqualTo(1);
    }

    @Test
    void confirmedDuplicateIsRegistered() {
        useCase.execute(caller, command(UUID.randomUUID(), sixFour(), regularTeams()));

        RegisterMatch.Result second = useCase.execute(caller, new RegisterMatch.Command(
                UUID.randomUUID(), PLAYED.plusSeconds(3600), sixFour(), regularTeams(), true));

        assertThat(second.created()).isTrue();
        assertThat(matches.count()).isEqualTo(2);
    }

    @Test
    void differentPairsOrDifferentDayAreNotDuplicates() {
        useCase.execute(caller, command(UUID.randomUUID(), sixFour(), regularTeams()));
        List<Participant> newPairs = List.of(
                Participant.player(Team.A, 1, ana.id()), Participant.player(Team.A, 2, caio.id()),
                Participant.player(Team.B, 1, beto.id()), Participant.player(Team.B, 2, dani.id()));

        useCase.execute(caller, command(UUID.randomUUID(), sixFour(), newPairs));
        useCase.execute(caller, new RegisterMatch.Command(UUID.randomUUID(), PLAYED.minus(Duration.ofDays(7)),
                sixFour(), regularTeams(), false));

        assertThat(matches.count()).isEqualTo(3);
    }

    @Test
    void futureMatchesAreRejectedBeyondTenMinutes() {
        RegisterMatch.Command tooLate = new RegisterMatch.Command(UUID.randomUUID(),
                NOW.plus(Duration.ofMinutes(10)).plusSeconds(1), sixFour(), regularTeams(), false);
        RegisterMatch.Command withinTolerance = new RegisterMatch.Command(UUID.randomUUID(),
                NOW.plus(Duration.ofMinutes(10)), sixFour(), regularTeams(), false);

        assertThatThrownBy(() -> useCase.execute(caller, tooLate)).hasMessage("MATCH_IN_FUTURE");
        assertThat(useCase.execute(caller, withinTolerance).created()).isTrue();
    }

    @Test
    void unknownPlayersAreRejected() {
        List<Participant> stranger = List.of(
                Participant.player(Team.A, 1, ana.id()), Participant.player(Team.A, 2, UUID.randomUUID()),
                Participant.player(Team.B, 1, caio.id()), Participant.player(Team.B, 2, dani.id()));

        assertThatThrownBy(() -> useCase.execute(caller, command(UUID.randomUUID(), sixFour(), stranger)))
                .hasMessage("UNKNOWN_PLAYER");
        assertThat(matches.count()).isZero();
    }

    @Test
    void callerDoesNotNeedToBeAParticipant() {
        Player eva = players.add("Eva");
        AuthenticatedUser evaCaller = new AuthenticatedUser(eva.identity(), null, "Eva", false);

        MatchDetails details = useCase.execute(evaCaller, command(UUID.randomUUID(), sixFour(), regularTeams())).details();

        assertThat(details.match().registeredBy()).isEqualTo(eva.id());
    }

    private Season activeSeason() {
        Instant created = Instant.parse("2026-09-01T12:00:00Z");
        return seasons.add(new Season(UUID.randomUUID(), "Primavera", LocalDate.of(2026, 10, 1),
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
