package com.lucchetta.core.domain;

import com.lucchetta.core.domain.tournament.Placement;
import com.lucchetta.core.exception.RuleViolationException;

/**
 * Points each player gets for their final placement in a daily tournament stage (D-028, D-030). A
 * better placement never gives fewer points than a worse one.
 */
public record PlacementPoints(int champion, int runnerUp, int thirdPlace, int fourthPlace, int quarterfinal,
                              int roundOf16, int groupStage) {

    public static final int MAX_POINTS = 1000;
    public static final PlacementPoints DEFAULT = new PlacementPoints(100, 70, 55, 45, 35, 25, 20);

    public PlacementPoints {
        int[] ordered = {champion, runnerUp, thirdPlace, fourthPlace, quarterfinal, roundOf16, groupStage};
        for (int i = 0; i < ordered.length; i++) {
            if (ordered[i] < 0 || ordered[i] > MAX_POINTS || (i > 0 && ordered[i] > ordered[i - 1])) {
                throw new RuleViolationException("INVALID_PLACEMENT_POINTS");
            }
        }
    }

    public int pointsFor(Placement placement) {
        return switch (placement) {
            case CHAMPION -> champion;
            case RUNNER_UP -> runnerUp;
            case THIRD -> thirdPlace;
            // A stage whose final was drawn before the third place game existed (D-030).
            case FOURTH, SEMIFINAL -> fourthPlace;
            case QUARTERFINAL -> quarterfinal;
            case ROUND_OF_16 -> roundOf16;
            case GROUP_STAGE -> groupStage;
        };
    }
}
