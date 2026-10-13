package com.lucchetta.core.domain.match;

import com.lucchetta.core.exception.RuleViolationException;

import java.util.Objects;

/**
 * Final score as informed by the user, stored separately from ranking points.
 *
 * @param tiebreakTeamAPoints optional, history only (D-002)
 * @param tiebreakTeamBPoints optional, history only (D-002)
 */
public record Score(MatchFormat format, int teamAGames, int teamBGames,
                    Integer tiebreakTeamAPoints, Integer tiebreakTeamBPoints) {

    public Score {
        Objects.requireNonNull(format, "format");
        if (teamAGames == teamBGames) {
            throw new RuleViolationException("INVALID_SCORE");
        }
        int winnerGames = Math.max(teamAGames, teamBGames);
        int loserGames = Math.min(teamAGames, teamBGames);
        if (!format.isValidFinalScore(winnerGames, loserGames)) {
            throw new RuleViolationException("INVALID_SCORE");
        }
        validateTiebreak(format.isTiebreakScore(winnerGames, loserGames),
                teamAGames > teamBGames ? Team.A : Team.B, tiebreakTeamAPoints, tiebreakTeamBPoints);
    }

    public Team winner() {
        return teamAGames > teamBGames ? Team.A : Team.B;
    }

    public int gamesOf(Team team) {
        return team == Team.A ? teamAGames : teamBGames;
    }

    public boolean decidedByTiebreak() {
        return format.isTiebreakScore(Math.max(teamAGames, teamBGames), Math.min(teamAGames, teamBGames));
    }

    private static void validateTiebreak(boolean tiebreakScore, Team winner, Integer a, Integer b) {
        if (a == null && b == null) {
            return;
        }
        if (!tiebreakScore || a == null || b == null || a < 0 || b < 0) {
            throw new RuleViolationException("INVALID_TIEBREAK");
        }
        Team tiebreakWinner = a > b ? Team.A : b > a ? Team.B : null;
        if (tiebreakWinner != winner) {
            throw new RuleViolationException("INVALID_TIEBREAK");
        }
    }
}
