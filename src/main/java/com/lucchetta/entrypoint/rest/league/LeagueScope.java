package com.lucchetta.entrypoint.rest.league;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.league.DefaultLeague;
import com.lucchetta.core.exception.BusinessException;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.league.LeagueMembership;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The league a request acts on. Season, match, ranking and player endpoints answer both under
 * {@code /api/leagues/{leagueId}/...} and at their original paths without a league, used by the
 * app published before leagues. Those are bound to the "Beach Tennis Arena" league (decision
 * D-023), which the caller joins on first use, as anyone may join a public league.
 */
@Component
public class LeagueScope {

    private final LeagueAccess leagueAccess;
    private final LeagueMembership leagueMembership;

    public LeagueScope(LeagueAccess leagueAccess, LeagueMembership leagueMembership) {
        this.leagueAccess = leagueAccess;
        this.leagueMembership = leagueMembership;
    }

    /** @param leagueId the league of the path, or null for the endpoints without a league */
    public UUID resolve(UUID leagueId, AuthenticatedUser user) {
        if (leagueId != null) {
            return leagueId;
        }
        if (!leagueAccess.context(user, DefaultLeague.ID).member()) {
            try {
                leagueMembership.join(user, DefaultLeague.ID);
            } catch (BusinessException e) {
                // Not joinable (made private, archived or full): the use case answers as for any
                // non-member.
            }
        }
        return DefaultLeague.ID;
    }
}
