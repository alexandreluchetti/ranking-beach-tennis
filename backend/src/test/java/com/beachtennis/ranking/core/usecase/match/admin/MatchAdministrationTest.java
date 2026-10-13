package com.beachtennis.ranking.core.usecase.match.admin;

import com.beachtennis.ranking.core.domain.AuthenticatedUser;
import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.domain.match.Match;
import com.beachtennis.ranking.core.domain.match.MatchAuditEntry;
import com.beachtennis.ranking.core.domain.match.MatchFormat;
import com.beachtennis.ranking.core.domain.match.MatchStatus;
import com.beachtennis.ranking.core.domain.match.Participant;
import com.beachtennis.ranking.core.domain.match.Score;
import com.beachtennis.ranking.core.domain.match.Team;
import com.beachtennis.ranking.core.exception.ConflictException;
import com.beachtennis.ranking.core.exception.ForbiddenException;
import com.beachtennis.ranking.core.exception.NotFoundException;
import com.beachtennis.ranking.core.scoring.NotCountedReason;
import com.beachtennis.ranking.core.stats.RankingEntry;
import com.beachtennis.ranking.core.support.DirectTransactionRunner;
import com.beachtennis.ranking.core.support.InMemoryMatchAudit;
import com.beachtennis.ranking.core.usecase.match.MatchDetails;
import com.beachtennis.ranking.core.usecase.match.MatchValidator;
import com.beachtennis.ranking.core.usecase.player.GetOrCreateCurrentPlayer;
import com.beachtennis.ranking.core.usecase.ranking.GetSeasonRanking;
import com.beachtennis.ranking.core.usecase.ranking.RankingFixture;
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
    private final AuthenticatedUser admin = new AuthenticatedUser(adminPlayer.identity(), null, "Admin", true);
    private final AuthenticatedUser regular = new AuthenticatedUser(world.ana.identity(), null, "Ana", false);
    private final MatchAdministration administration = new MatchAdministration(
            new GetOrCreateCurrentPlayer(world.players, RankingFixture.CLOCK), world.matches, audit,
            new MatchValidator(world.players), world.assembler, new DirectTransactionRunner(), RankingFixture.CLOCK);
    private final GetSeasonRanking ranking = new GetSeasonRanking(world.querySeasons, world.matches, world.players,
            world.assembler);

    /** Ana+Beto beat Caio+Dani 6-4 in the active season: 9 points each for Ana and Beto. */
    private final Match match = world.match(LocalDate.of(2026, 10, 4), 6, 4);

    @Test
    void correctionChangesScoreRecalculatesPointsAndIsAudited() {
        MatchAdministration.Outcome outcome = administration.correct(admin,
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

        MatchDetails corrected = administration.correct(admin, new MatchAdministration.Correction(guestMatch.id(),
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
        administration.correct(admin, new MatchAdministration.Correction(match.id(), match.version(),
                LocalDate.of(2026, 9, 20).atStartOfDay(RankingFixture.BRASILIA).toInstant().plus(Duration.ofHours(10)),
                match.score(), match.participants(), "Data errada"));

        assertThat(pointsOf(world.ana)).isZero();
        assertThat(ranking.bySeasonId(world.pastSeason.id()).entries().getFirst().stats().points()).isEqualTo(9);
    }

    @Test
    void staleVersionIsRejectedWithoutChanges() {
        administration.correct(admin, correction(match.version(), new Score(MatchFormat.STANDARD, 6, 2, null, null),
                match.participants(), null));

        assertThatThrownBy(() -> administration.correct(admin, correction(match.version(),
                new Score(MatchFormat.STANDARD, 6, 3, null, null), match.participants(), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("MATCH_MODIFIED");
        assertThat(world.matches.findById(match.id()).orElseThrow().score().teamBGames()).isEqualTo(2);
        assertThat(audit.entries()).hasSize(1);
    }

    @Test
    void correctionWithoutChangesIsNotAudited() {
        MatchAdministration.Outcome outcome = administration.correct(admin,
                correction(match.version(), match.score(), match.participants(), "nada"));

        assertThat(outcome.changed()).isFalse();
        assertThat(audit.entries()).isEmpty();
        assertThat(world.matches.findById(match.id()).orElseThrow().version()).isEqualTo(match.version());
    }

    @Test
    void correctionAppliesRegistrationRules() {
        assertThatThrownBy(() -> administration.correct(admin, correction(match.version(), match.score(), List.of(
                Participant.player(Team.A, 1, world.ana.id()), Participant.player(Team.A, 2, UUID.randomUUID()),
                Participant.player(Team.B, 1, world.caio.id()), Participant.player(Team.B, 2, world.dani.id())), null)))
                .hasMessage("UNKNOWN_PLAYER");
        assertThatThrownBy(() -> administration.correct(admin, new MatchAdministration.Correction(match.id(),
                match.version(), RankingFixture.NOW.plus(Duration.ofHours(1)), match.score(), match.participants(),
                null)))
                .hasMessage("MATCH_IN_FUTURE");
        assertThat(audit.entries()).isEmpty();
    }

    @Test
    void voidRemovesPointsAndStatisticsKeepsHistoryAndIsAudited() {
        MatchDetails voided = administration.voidMatch(admin, match.id(), "  Partida de treino  ").details();

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
        assertThatThrownBy(() -> administration.voidMatch(admin, match.id(), "  ")).hasMessage("REASON_REQUIRED");
        assertThatThrownBy(() -> administration.voidMatch(admin, match.id(), "x".repeat(501)))
                .hasMessage("REASON_TOO_LONG");
        assertThatThrownBy(() -> administration.revertVoid(admin, match.id(), "motivo"))
                .hasMessage("MATCH_NOT_VOIDED");

        administration.voidMatch(admin, match.id(), "motivo");

        assertThatThrownBy(() -> administration.voidMatch(admin, match.id(), "de novo"))
                .hasMessage("MATCH_ALREADY_VOIDED");
        assertThatThrownBy(() -> administration.revertVoid(admin, match.id(), null)).hasMessage("REASON_REQUIRED");
        assertThatThrownBy(() -> administration.correct(admin, correction(match.version() + 1, match.score(),
                match.participants(), null)))
                .hasMessage("MATCH_VOIDED");
    }

    @Test
    void revertRestoresPointsAndRepeatedCyclesNeverDuplicateThem() {
        for (int i = 0; i < 3; i++) {
            administration.voidMatch(admin, match.id(), "anulação " + i);
            assertThat(pointsOf(world.ana)).isZero();
            MatchDetails restored = administration.revertVoid(admin, match.id(), "reversão " + i).details();
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
        assertThatThrownBy(() -> administration.voidMatch(regular, match.id(), "motivo"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> administration.revertVoid(regular, match.id(), "motivo"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> administration.correct(regular, correction(match.version(), match.score(),
                match.participants(), null)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(world.matches.findById(match.id()).orElseThrow()).isEqualTo(match);
        assertThat(audit.entries()).isEmpty();
    }

    @Test
    void unknownMatch() {
        assertThatThrownBy(() -> administration.voidMatch(admin, UUID.randomUUID(), "motivo"))
                .isInstanceOf(NotFoundException.class);
    }

    private MatchAdministration.Correction correction(int version, Score score, List<Participant> participants,
                                                      String reason) {
        return new MatchAdministration.Correction(match.id(), version, match.playedAt(), score, participants, reason);
    }

    private int pointsOf(Player player) {
        return entry(player).stats().points();
    }

    private RankingEntry entry(Player player) {
        return ranking.current().orElseThrow().entries().stream()
                .filter(e -> e.playerId().equals(player.id()))
                .findFirst().orElseThrow();
    }
}
