package com.lucchetta.core.domain.tournament;

/** Games won by each pair in a scored fixture (tie-break counted as one game, D-002). */
public record FixtureResult(int pairAGames, int pairBGames) {

    public boolean pairAWon() {
        return pairAGames > pairBGames;
    }
}
