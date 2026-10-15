package com.lucchetta.entrypoint.rest.league;

import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.domain.league.LeagueVisibility;

/** Request bodies of the league endpoints. */
public final class LeagueRequests {

    private LeagueRequests() {
    }

    public record CreateLeagueRequest(String name, String description, LeagueVisibility visibility) {
    }

    /** Null fields stay unchanged; an empty description removes it. */
    public record UpdateLeagueRequest(String name, String description, LeagueVisibility visibility) {
    }

    public record ChangeRoleRequest(LeagueRole role) {
    }
}
