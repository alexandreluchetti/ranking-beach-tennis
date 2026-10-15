package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueRole;

/**
 * A league as seen by the caller.
 *
 * @param myRole             the caller's role, or null if not a member
 * @param canAdminister      league administrator, or the system administrator (decision D-018)
 * @param joinRequestPending the caller asked to join and no league administrator answered yet
 */
public record LeagueView(League league, int memberCount, LeagueRole myRole, boolean canAdminister,
                         boolean joinRequestPending) {

    public int memberLimit() {
        return League.MAX_MEMBERS;
    }

    public boolean member() {
        return myRole != null;
    }
}
