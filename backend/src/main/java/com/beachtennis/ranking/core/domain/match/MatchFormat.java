package com.beachtennis.ranking.core.domain.match;

/**
 * Match formats and their valid final scores (decision D-002).
 * <p>
 * With target {@code T} games: {@code T–0 … T–(T-2)}, {@code (T+1)–(T-1)} and
 * {@code (T+1)–T}, the last one decided by a tie-break that counts as one game.
 */
public enum MatchFormat {
    /** To 6 games: 6–0…6–4, 7–5, 7–6 (tie-break). */
    STANDARD(6),
    /** To 4 games: 4–0…4–2, 5–3, 5–4 (tie-break). */
    SHORT(4);

    private final int targetGames;

    MatchFormat(int targetGames) {
        this.targetGames = targetGames;
    }

    public boolean isValidFinalScore(int winnerGames, int loserGames) {
        if (loserGames < 0) {
            return false;
        }
        if (winnerGames == targetGames) {
            return loserGames <= targetGames - 2;
        }
        return winnerGames == targetGames + 1 && (loserGames == targetGames - 1 || loserGames == targetGames);
    }

    public boolean isTiebreakScore(int winnerGames, int loserGames) {
        return winnerGames == targetGames + 1 && loserGames == targetGames;
    }
}
