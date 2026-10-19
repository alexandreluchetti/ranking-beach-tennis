package com.lucchetta.core.scoring;

import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.domain.match.Score;
import com.lucchetta.core.domain.match.Team;
import com.lucchetta.core.support.TestLeague;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringAndEligibilityTest {

    private static final Instant PLAYED = Instant.parse("2026-10-11T12:00:00Z");
    private static final Season SEASON = new Season(UUID.randomUUID(), TestLeague.ID, "Primavera",
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), UUID.randomUUID(), PLAYED, PLAYED);

    private final ScoringPolicy scoring = new GamesPlusWinBonusScoringPolicy();
    private final RankingEligibilityPolicy eligibility = new RankingEligibilityPolicy();

    /** The exact table approved in decision D-003. */
    @ParameterizedTest(name = "{0} {1}-{2}: winners {3}, losers {4}")
    @CsvSource({
            "STANDARD,6,4,9,4",
            "STANDARD,7,5,10,5",
            "STANDARD,7,6,10,6",
            "STANDARD,6,0,9,0",
            "SHORT,4,2,7,2",
            "SHORT,5,4,8,4"})
    void gamesWonPlusThreeForTheWinners(MatchFormat format, int winnerGames, int loserGames,
                                        int winnerPoints, int loserPoints) {
        Match teamAWins = match(new Score(format, winnerGames, loserGames, null, null), registeredOnly());
        Match teamBWins = match(new Score(format, loserGames, winnerGames, null, null), registeredOnly());

        assertThat(scoring.pointsPerPlayer(teamAWins, Team.A)).isEqualTo(winnerPoints);
        assertThat(scoring.pointsPerPlayer(teamAWins, Team.B)).isEqualTo(loserPoints);
        assertThat(scoring.pointsPerPlayer(teamBWins, Team.B)).isEqualTo(winnerPoints);
        assertThat(scoring.pointsPerPlayer(teamBWins, Team.A)).isEqualTo(loserPoints);
    }

    @Test
    void tiebreakPointsDoNotAffectRankingPoints() {
        Match close = match(new Score(MatchFormat.STANDARD, 7, 6, 7, 5), registeredOnly());
        Match long_ = match(new Score(MatchFormat.STANDARD, 7, 6, 15, 13), registeredOnly());

        assertThat(scoring.pointsPerPlayer(close, Team.A)).isEqualTo(10).isEqualTo(scoring.pointsPerPlayer(long_, Team.A));
        assertThat(scoring.pointsPerPlayer(close, Team.B)).isEqualTo(6).isEqualTo(scoring.pointsPerPlayer(long_, Team.B));
    }

    @Test
    void validMatchWithRegisteredPlayersInsideSeasonCounts() {
        assertThat(eligibility.evaluate(match(sixFour(), registeredOnly()), SEASON))
                .isEqualTo(RankingEligibility.COUNTED);
    }

    @Test
    void guestsDoNotChangeWhetherAMatchCounts() {
        assertThat(eligibility.evaluate(match(sixFour(), withGuest()), SEASON))
                .isEqualTo(RankingEligibility.COUNTED);
    }

    @Test
    void matchOutsideAnySeasonDoesNotCount() {
        assertThat(eligibility.evaluate(match(sixFour(), registeredOnly()), null))
                .isEqualTo(RankingEligibility.notCounted(NotCountedReason.NO_SEASON));
    }

    @Test
    void reasonPrecedenceIsVoidedThenNoSeason() {
        Match voidedWithGuest = new Match(UUID.randomUUID(), TestLeague.ID, PLAYED, sixFour(), withGuest(),
                MatchStatus.VOIDED, UUID.randomUUID(), PLAYED, PLAYED);

        assertThat(eligibility.evaluate(voidedWithGuest, null).reason()).isEqualTo(NotCountedReason.VOIDED);
        assertThat(eligibility.evaluate(match(sixFour(), withGuest()), null).reason())
                .isEqualTo(NotCountedReason.NO_SEASON);
    }

    private static Score sixFour() {
        return new Score(MatchFormat.STANDARD, 6, 4, null, null);
    }

    private static List<Participant> registeredOnly() {
        return List.of(Participant.player(Team.A, 1, UUID.randomUUID()), Participant.player(Team.A, 2, UUID.randomUUID()),
                Participant.player(Team.B, 1, UUID.randomUUID()), Participant.player(Team.B, 2, UUID.randomUUID()));
    }

    private static List<Participant> withGuest() {
        return List.of(Participant.player(Team.A, 1, UUID.randomUUID()), Participant.player(Team.A, 2, UUID.randomUUID()),
                Participant.player(Team.B, 1, UUID.randomUUID()), Participant.guest(Team.B, 2, null));
    }

    private static Match match(Score score, List<Participant> participants) {
        return new Match(UUID.randomUUID(), TestLeague.ID, PLAYED, score, participants, MatchStatus.VALID,
                UUID.randomUUID(), PLAYED, PLAYED);
    }
}
