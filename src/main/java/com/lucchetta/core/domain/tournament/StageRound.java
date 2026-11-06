package com.lucchetta.core.domain.tournament;

/**
 * Rounds of a daily tournament stage, in playing order (D-027, D-030). The third place game and
 * the final are drawn together, from the semifinals, and form the last level.
 */
public enum StageRound {
    GROUP(0),
    ROUND_OF_16(1),
    QUARTERFINAL(2),
    SEMIFINAL(3),
    THIRD_PLACE(4),
    FINAL(4);

    private final int level;

    StageRound(int level) {
        this.level = level;
    }

    /** Rounds of a higher level depend on the results of this one. */
    public boolean before(StageRound other) {
        return level < other.level;
    }
}
