package com.lucchetta.entrypoint.rest.league;

import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.domain.league.LeagueVisibility;
import com.lucchetta.core.usecase.league.LeagueView;

import java.util.UUID;

/**
 * @param memberCount        members right now; shown with {@code memberLimit} as "13/50"
 * @param myRole             the caller's role, null if not a member
 * @param canAdminister      whether the caller may administer the league (league administrator or
 *                           system administrator)
 * @param joinRequestPending the caller asked to join this private league and is waiting for an answer
 */
public record LeagueResponse(
        UUID id,
        String name,
        String description,
        LeagueVisibility visibility,
        int memberCount,
        int memberLimit,
        boolean archived,
        LeagueRole myRole,
        boolean canAdminister,
        boolean joinRequestPending) {

    public static LeagueResponse from(LeagueView view) {
        return new LeagueResponse(view.league().id(), view.league().name(), view.league().description(),
                view.league().visibility(), view.memberCount(), view.memberLimit(), view.league().archived(),
                view.myRole(), view.canAdminister(), view.joinRequestPending());
    }
}
