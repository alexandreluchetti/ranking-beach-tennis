package com.lucchetta.core.domain;

/** How a season's ranking is computed (decision D-026). */
public enum RankingType {
    /** Type A: points per match, from games won and the win bonus (D-003). */
    MATCH_POINTS,
    /** Type B: each playing day holds a stage; points come from the placement in each stage (D-027, D-028). */
    DAILY_TOURNAMENT
}
