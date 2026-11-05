package com.lucchetta.core.domain;

import com.lucchetta.core.exception.RuleViolationException;

/**
 * Points each player gets for their final placement in a daily tournament stage (D-028). A better
 * placement never gives fewer points than a worse one.
 */
public record PlacementPoints(int champion, int runnerUp, int semifinal, int quarterfinal, int groupStage) {

    public static final int MAX_POINTS = 1000;
    public static final PlacementPoints DEFAULT = new PlacementPoints(100, 70, 50, 35, 20);

    public PlacementPoints {
        int[] ordered = {champion, runnerUp, semifinal, quarterfinal, groupStage};
        for (int i = 0; i < ordered.length; i++) {
            if (ordered[i] < 0 || ordered[i] > MAX_POINTS || (i > 0 && ordered[i] > ordered[i - 1])) {
                throw new RuleViolationException("INVALID_PLACEMENT_POINTS");
            }
        }
    }
}
