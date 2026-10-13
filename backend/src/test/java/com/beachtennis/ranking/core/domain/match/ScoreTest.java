package com.beachtennis.ranking.core.domain.match;

import com.beachtennis.ranking.core.exception.RuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Decision D-002. */
class ScoreTest {

    @ParameterizedTest(name = "{0} {1}-{2} is valid")
    @CsvSource({
            "STANDARD,6,0", "STANDARD,6,1", "STANDARD,6,2", "STANDARD,6,3", "STANDARD,6,4",
            "STANDARD,7,5", "STANDARD,7,6",
            "SHORT,4,0", "SHORT,4,1", "SHORT,4,2", "SHORT,5,3", "SHORT,5,4"})
    void acceptsEveryValidFinalScoreForEitherTeam(MatchFormat format, int winner, int loser) {
        assertThat(new Score(format, winner, loser, null, null).winner()).isEqualTo(Team.A);
        assertThat(new Score(format, loser, winner, null, null).winner()).isEqualTo(Team.B);
    }

    @ParameterizedTest(name = "{0} {1}-{2} is invalid")
    @CsvSource({
            "STANDARD,6,5", "STANDARD,6,6", "STANDARD,5,4", "STANDARD,7,4", "STANDARD,7,7", "STANDARD,8,6",
            "STANDARD,0,0", "STANDARD,4,2",
            "SHORT,4,3", "SHORT,4,4", "SHORT,3,1", "SHORT,5,2", "SHORT,6,4", "SHORT,6,5",
            "STANDARD,-1,6"})
    void rejectsInvalidFinalScores(MatchFormat format, int teamA, int teamB) {
        assertThatThrownBy(() -> new Score(format, teamA, teamB, null, null))
                .isInstanceOf(RuleViolationException.class)
                .hasMessage("INVALID_SCORE");
    }

    @Test
    void tiebreakPointsAreOptionalForTiebreakScores() {
        Score withoutPoints = new Score(MatchFormat.STANDARD, 7, 6, null, null);
        Score withPoints = new Score(MatchFormat.STANDARD, 6, 7, 5, 7);

        assertThat(withoutPoints.decidedByTiebreak()).isTrue();
        assertThat(withPoints.winner()).isEqualTo(Team.B);
        assertThat(withPoints.tiebreakTeamBPoints()).isEqualTo(7);
        assertThat(new Score(MatchFormat.SHORT, 5, 4, 10, 8).decidedByTiebreak()).isTrue();
    }

    @Test
    void tiebreakPointsOnlyAllowedForTiebreakScores() {
        assertThatThrownBy(() -> new Score(MatchFormat.STANDARD, 6, 4, 7, 5)).hasMessage("INVALID_TIEBREAK");
        assertThatThrownBy(() -> new Score(MatchFormat.STANDARD, 7, 5, 7, 5)).hasMessage("INVALID_TIEBREAK");
    }

    @Test
    void tiebreakWinnerMustBeTheMatchWinner() {
        assertThatThrownBy(() -> new Score(MatchFormat.STANDARD, 7, 6, 5, 7)).hasMessage("INVALID_TIEBREAK");
        assertThatThrownBy(() -> new Score(MatchFormat.STANDARD, 7, 6, 6, 6)).hasMessage("INVALID_TIEBREAK");
    }

    @Test
    void tiebreakNeedsBothNonNegativeValues() {
        assertThatThrownBy(() -> new Score(MatchFormat.STANDARD, 7, 6, 7, null)).hasMessage("INVALID_TIEBREAK");
        assertThatThrownBy(() -> new Score(MatchFormat.STANDARD, 7, 6, 7, -1)).hasMessage("INVALID_TIEBREAK");
    }

    @Test
    void gamesPerTeam() {
        Score score = new Score(MatchFormat.STANDARD, 4, 6, null, null);

        assertThat(score.gamesOf(Team.A)).isEqualTo(4);
        assertThat(score.gamesOf(Team.B)).isEqualTo(6);
        assertThat(score.decidedByTiebreak()).isFalse();
    }
}
