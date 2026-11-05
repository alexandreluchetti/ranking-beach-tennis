package com.lucchetta.core.domain.tournament;

import java.util.UUID;

/**
 * A game of a stage between two pairs. Pair A plays as team A of the match.
 *
 * @param number  playing order inside the stage, from 1
 * @param group   group number for group games, null for knockout games
 * @param matchId the match holding its score, or null while it has none
 */
public record StageFixture(int number, StageRound round, Integer group, int pairA, int pairB, UUID matchId) {

    public boolean scored() {
        return matchId != null;
    }

    public StageFixture withMatch(UUID newMatchId) {
        return new StageFixture(number, round, group, pairA, pairB, newMatchId);
    }
}
