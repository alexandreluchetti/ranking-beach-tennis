package com.lucchetta.core.domain.tournament;

/** Final placement of a pair in a stage (D-028, D-030), best first. */
public enum Placement {
    CHAMPION,
    RUNNER_UP,
    THIRD,
    FOURTH,
    /**
     * Lost a semifinal of a stage whose final was drawn before the third place game existed
     * (D-030); worth the fourth place points.
     */
    SEMIFINAL,
    QUARTERFINAL,
    ROUND_OF_16,
    GROUP_STAGE
}
