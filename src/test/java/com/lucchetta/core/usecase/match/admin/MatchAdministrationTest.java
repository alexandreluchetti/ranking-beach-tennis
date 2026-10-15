package com.lucchetta.core.usecase.match.admin;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchAuditEntry;
import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.domain.match.Score;
import com.lucchetta.core.domain.match.Team;
import com.lucchetta.core.domain.notification.DevicePlatform;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.ForbiddenException;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.scoring.NotCountedReason;
import com.lucchetta.core.stats.RankingEntry;
import com.lucchetta.core.support.DirectTransactionRunner;
import com.lucchetta.core.support.InMemoryMatchAudit;
import com.lucchetta.core.support.InMemoryNotifications;
import com.lucchetta.core.support.TestLeague;
import com.lucchetta.core.usecase.notification.MatchNotificationComposer;
import com.lucchetta.core.usecase.notification.MatchNotifier;
import com.lucchetta.core.usecase.notification.PushDispatcher;
import com.lucchetta.core.usecase.match.MatchDetails;
import com.lucchetta.core.usecase.match.MatchValidator;
import com.lucchetta.core.usecase.ranking.GetSeasonRanking;
import com.lucchetta.core.usecase.ranking.RankingFixture;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchAdministrationTest {

    private final RankingFixture world = new RankingFixture();
    private final InMemoryMatchAudit audit = new InMemoryMatchAudit();
    private final Player adminPlayer = world.players.add("Admin");
    /** A league administrator, not the system administrator. */
    private final AuthenticatedUser admin = new AuthenticatedUser(adminPlayer.identity(), null, "Admin", false);
    private final AuthenticatedUser regular = new AuthenticatedUser(world.ana.identity(), null, "Ana", false);
    private final InMemoryNotifications notifications = new InMemoryNotifications();
    private final MatchAdministration administration = new MatchAdministration(
            world.access, world.matches, audit, new MatchValidator(world.league.leagues), world.assembler,
            new MatchNotifier(notifications, world.league.leagues,
                    new MatchNotificationComposer(), RankingFixture.CLOCK),
            new PushDispatcher(notifications, notifications), new DirectTransactionRunner(), RankingFixture.CLOCK);
    private final GetSeasonRanking ranking = new GetSeasonRanking(world.access, world.querySeasons, world.matches,
            world.players, world.league.leagues, world.assembler);

    {
        world.league.administrator(adminPlayer);
    }

    /** Ana+Beto beat Caio+Dani 6-4 in the active season: 9 points each for Ana and Beto. */
    private final Match match = world.match(LocalDate.of(2026, 10, 4), 6, 4);

    @Test
    void correctionChangesScoreRecalculatesPointsAndIsAudited() {
        MatchAdministration.Outcome outcome = correct(admin,
                correction(match.version(), new Score(MatchFormat.STANDARD, 6, 2, null, null), match.participants(),
                        "Placar anotado errado"));

        assertThat(outcome.changed()).isTrue();
        assertThat(outcome.details().match().score().teamBGames()).isEqualTo(2);
        assertThat(outcome.details().pointsFor(Team.B)).isEqualTo(2);
        assertThat(outcome.details().match().version()).isEqualTo(match.version() + 1);
        assertThat(pointsOf(world.caio)).isEqualTo(2);
        assertThat(pointsOf(world.ana)).isEqualTo(9);

        MatchAuditEntry entry = audit.entries().getFirst();
        assertThat(entry.action()).isEqualTo(MatchAuditEntry.Action.CORRECTED);
        assertThat(entry.performedBy()).isEqualTo(adminPlayer.id());
        assertThat(entry.performedAt()).isEqualTo(RankingFixture.NOW);
        assertThat(entry.reason()).isEqualTo("Placar anotado errado");
        assertThat(entry.before().score().teamBGames()).isEqualTo(4);
        assertThat(entry.after().score().teamBGames()).isEqualTo(2);
    }

    @Test
    void correctionCanSwapParticipantsAndChangeEligibility() {
        Match guestMatch = world.match(LocalDate.of(2026, 10, 5), 6, 1, MatchStatus.VALID,
                world.anaAndGuestVsCaioAndDani());
        assertThat(pointsOf(world.ana)).isEqualTo(9);

        MatchDetails corrected = correct(admin, new MatchAdministration.Correction(guestMatch.id(),
                guestMatch.version(), guestMatch.playedAt(), guestMatch.score(), List.of(
                Participant.player(Team.A, 1, world.ana.id()), Participant.player(Team.A, 2, world.beto.id()),
                Participant.player(Team.B, 1, world.caio.id()), Participant.player(Team.B, 2, world.dani.id())),
                null)).details();

        assertThat(corrected.eligibility().counted()).isTrue();
        assertThat(pointsOf(world.ana)).isEqualTo(18);
        assertThat(pointsOf(world.beto)).isEqualTo(18);
        assertThat(audit.entries().getFirst().reason()).isNull();
    }

    @Test
    void correctionMayMoveTheMatchOutOfTheSeason() {
        correct(admin, new MatchAdministration.Correction(match.id(), match.version(),
                LocalDate.of(2026, 9, 20).atStartOfDay(RankingFixture.BRASILIA).toInstant().plus(Duration.ofHours(10)),
                match.score(), match.participants(), "Data errada"));

        assertThat(pointsOf(world.ana)).isZero();
        assertThat(seasonRanking(world.pastSeason.id()).entries().getFirst().stats().points()).isEqualTo(9);
    }

    @Test
    void staleVersionIsRejectedWithoutChanges() {
        correct(admin, correction(match.version(), new Score(MatchFormat.STANDARD, 6, 2, null, null),
                match.participants(), null));

        assertThatThrownBy(() -> correct(admin, correction(match.version(),
                new Score(MatchFormat.STANDARD, 6, 3, null, null), match.participants(), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("MATCH_MODIFIED");
        assertThat(world.matches.findById(match.id()).orElseThrow().score().teamBGames()).isEqualTo(2);
        assertThat(audit.entries()).hasSize(1);
    }

    @Test
    void correctionWithoutChangesIsNotAudited() {
        MatchAdministration.Outcome outcome = correct(admin,
                correction(match.version(), match.score(), match.participants(), "nada"));

        assertThat(outcome.changed()).isFalse();
        assertThat(audit.entries()).isEmpty();
        assertThat(world.matches.findById(match.id()).orElseThrow().version()).isEqualTo(match.version());
    }

    @Test
    void correctionAppliesRegistrationRules() {
        assertThatThrownBy(() -> correct(admin, correction(match.version(), match.score(), List.of(
                Participant.player(Team.A, 1, world.ana.id()), Participant.player(Team.A, 2, UUID.randomUUID()),
                Participant.player(Team.B, 1, world.caio.id()), Participant.player(Team.B, 2, world.dani.id())), null)))
                .hasMessage("PLAYER_NOT_LEAGUE_MEMBER");
        assertThatThrownBy(() -> correct(admin, new MatchAdministration.Correction(match.id(),
                match.version(), RankingFixture.NOW.plus(Duration.ofHours(1)), match.score(), match.participants(),
                null)))
                .hasMessage("MATCH_IN_FUTURE");
        assertThat(audit.entries()).isEmpty();
    }

    @Test
    void voidRemovesPointsAndStatisticsKeepsHistoryAndIsAudited() {
        MatchDetails voided = voidMatch(admin, match.id(), "  Partida de treino  ").details();

        assertThat(voided.match().status()).isEqualTo(MatchStatus.VOIDED);
        assertThat(voided.match().voiding().reason()).isEqualTo("Partida de treino");
        assertThat(voided.match().voiding().voidedBy()).isEqualTo(adminPlayer.id());
        assertThat(voided.eligibility().reason()).isEqualTo(NotCountedReason.VOIDED);
        assertThat(voided.pointsFor(Team.A)).isZero();
        assertThat(world.matches.findById(match.id())).isPresent();

        RankingEntry ana = entry(world.ana);
        assertThat(ana.stats().points()).isZero();
        assertThat(ana.stats().matchesPlayed()).isZero();

        assertThat(audit.entries().getFirst().action()).isEqualTo(MatchAuditEntry.Action.VOIDED);
        assertThat(audit.entries().getFirst().reason()).isEqualTo("Partida de treino");
    }

    @Test
    void voidAndRevertRequireReasonAndValidState() {
        assertThatThrownBy(() -> voidMatch(admin, match.id(), "  ")).hasMessage("REASON_REQUIRED");
        assertThatThrownBy(() -> voidMatch(admin, match.id(), "x".repeat(501)))
                .hasMessage("REASON_TOO_LONG");
        assertThatThrownBy(() -> revertVoid(admin, match.id(), "motivo"))
                .hasMessage("MATCH_NOT_VOIDED");

        voidMatch(admin, match.id(), "motivo");

        assertThatThrownBy(() -> voidMatch(admin, match.id(), "de novo"))
                .hasMessage("MATCH_ALREADY_VOIDED");
        assertThatThrownBy(() -> revertVoid(admin, match.id(), null)).hasMessage("REASON_REQUIRED");
        assertThatThrownBy(() -> correct(admin, correction(match.version() + 1, match.score(),
                match.participants(), null)))
                .hasMessage("MATCH_VOIDED");
    }

    @Test
    void revertRestoresPointsAndRepeatedCyclesNeverDuplicateThem() {
        for (int i = 0; i < 3; i++) {
            voidMatch(admin, match.id(), "anulação " + i);
            assertThat(pointsOf(world.ana)).isZero();
            MatchDetails restored = revertVoid(admin, match.id(), "reversão " + i).details();
            assertThat(restored.match().voiding()).isNull();
            assertThat(restored.eligibility().counted()).isTrue();
            assertThat(pointsOf(world.ana)).isEqualTo(9);
        }
        assertThat(audit.entries()).extracting(MatchAuditEntry::action).containsExactly(
                MatchAuditEntry.Action.VOIDED, MatchAuditEntry.Action.VOID_REVERTED,
                MatchAuditEntry.Action.VOIDED, MatchAuditEntry.Action.VOID_REVERTED,
                MatchAuditEntry.Action.VOIDED, MatchAuditEntry.Action.VOID_REVERTED);
    }

    @Test
    void onlyTheAdministratorMayChangeMatches() {
        assertThatThrownBy(() -> voidMatch(regular, match.id(), "motivo"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> revertVoid(regular, match.id(), "motivo"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> correct(regular, correction(match.version(), match.score(),
                match.participants(), null)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(world.matches.findById(match.id()).orElseThrow()).isEqualTo(match);
        assertThat(audit.entries()).isEmpty();
    }

    @Test
    void correctionNotifiesRemovedAndIncludedPlayersAndTheAdministrator() {
        Player eva = world.member("Eva", RankingFixture.NOW);
        correct(admin, correction(match.version(), match.score(), List.of(
                Participant.player(Team.A, 1, world.ana.id()), Participant.player(Team.A, 2, world.beto.id()),
                Participant.player(Team.B, 1, world.caio.id()), Participant.player(Team.B, 2, eva.id())), null));

        assertThat(notifications.all()).extracting(n -> n.recipientId()).containsExactlyInAnyOrder(
                world.ana.id(), world.beto.id(), world.caio.id(), world.dani.id(), eva.id(), adminPlayer.id());
        assertThat(notifications.of(world.dani.id()).getFirst().body())
                .contains("Você não faz mais parte desta partida.", "Seus pontos nesta partida: 4 → 0.");
        assertThat(notifications.of(eva.id()).getFirst().body())
                .contains("Você foi incluído nesta partida.", "Seus pontos nesta partida: 0 → 4.");
        assertThat(notifications.of(world.ana.id()).getFirst().body())
                .contains("duplas Ana e Beto x Caio e Dani → Ana e Beto x Caio e Eva", "9 → 9");
    }

    @Test
    void voidAndRevertNotifyEveryTimeButNeverTwiceForTheSameEvent() {
        notifications.save("ana-phone", world.ana.id(), DevicePlatform.ANDROID, RankingFixture.NOW);
        notifications.save("admin-phone", adminPlayer.id(), DevicePlatform.IOS, RankingFixture.NOW);

        voidMatch(admin, match.id(), "Partida de treino");
        revertVoid(admin, match.id(), "Anulada por engano");

        List<String> anaBodies = notifications.of(world.ana.id()).stream().map(n -> n.body()).toList();
        assertThat(anaBodies).hasSize(2);
        assertThat(anaBodies.get(0)).contains("foi anulada", "Motivo: Partida de treino.", "9 → 0");
        assertThat(anaBodies.get(1)).contains("voltou a valer", "Motivo: Anulada por engano.", "0 → 9");
        assertThat(notifications.of(adminPlayer.id())).hasSize(2);
        // The administrator performed both actions: in-app record only, no push to their phone.
        assertThat(notifications.pushedTokens()).containsExactly("ana-phone", "ana-phone");
        assertThat(notifications.all()).hasSize(10);
    }

    @Test
    void unknownMatch() {
        assertThatThrownBy(() -> voidMatch(admin, UUID.randomUUID(), "motivo"))
                .isInstanceOf(NotFoundException.class);
    }

    private MatchAdministration.Correction correction(int version, Score score, List<Participant> participants,
                                                      String reason) {
        return new MatchAdministration.Correction(match.id(), version, match.playedAt(), score, participants, reason);
    }

    @Test
    void systemAdministratorActsInAnyLeagueWithoutBeingAMember() {
        Player system = world.players.add("Sistema");
        AuthenticatedUser systemAdmin = new AuthenticatedUser(system.identity(), null, "Sistema", true);

        MatchDetails voided = voidMatch(systemAdmin, match.id(), "Partida de treino").details();

        assertThat(voided.match().voiding().voidedBy()).isEqualTo(system.id());
        assertThat(notifications.of(system.id())).isEmpty();
    }

    @Test
    void matchesOfAnotherLeagueAreNotFound() {
        assertThatThrownBy(() -> administration.voidMatch(admin, UUID.randomUUID(), match.id(), "motivo"))
                .hasMessage("LEAGUE_NOT_FOUND");
    }

    private MatchAdministration.Outcome correct(AuthenticatedUser user, MatchAdministration.Correction correction) {
        return administration.correct(user, TestLeague.ID, correction);
    }

    private MatchAdministration.Outcome voidMatch(AuthenticatedUser user, UUID matchId, String reason) {
        return administration.voidMatch(user, TestLeague.ID, matchId, reason);
    }

    private MatchAdministration.Outcome revertVoid(AuthenticatedUser user, UUID matchId, String reason) {
        return administration.revertVoid(user, TestLeague.ID, matchId, reason);
    }

    private GetSeasonRanking.SeasonRanking seasonRanking(UUID seasonId) {
        return ranking.bySeasonId(admin, TestLeague.ID, seasonId);
    }

    private int pointsOf(Player player) {
        return entry(player).stats().points();
    }

    private RankingEntry entry(Player player) {
        return ranking.current(admin, TestLeague.ID).orElseThrow().entries().stream()
                .filter(e -> e.playerId().equals(player.id()))
                .findFirst().orElseThrow();
    }
}
