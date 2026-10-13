package com.lucchetta.core.stats;

/**
 * A player's numbers over a set of matches.
 *
 * @param points ranking points (only from matches that count, D-003/D-004)
 */
public record PlayerStats(int matchesPlayed, int wins, int losses, int gamesWon, int gamesLost, int points) {

    public static final PlayerStats EMPTY = new PlayerStats(0, 0, 0, 0, 0, 0);

    /** Decision D-005: from the recorded score, tie-break counted as one game. */
    public int gameDifference() {
        return gamesWon - gamesLost;
    }

    PlayerStats plus(boolean won, int ownGames, int opponentGames, int matchPoints) {
        return new PlayerStats(matchesPlayed + 1, wins + (won ? 1 : 0), losses + (won ? 0 : 1),
                gamesWon + ownGames, gamesLost + opponentGames, points + matchPoints);
    }
}
