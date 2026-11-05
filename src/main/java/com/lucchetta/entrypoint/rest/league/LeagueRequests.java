package com.lucchetta.entrypoint.rest.league;

import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.domain.league.LeagueVisibility;
import com.lucchetta.core.domain.league.ScoreEntry;

/** Request bodies of the league endpoints. */
public final class LeagueRequests {

    private LeagueRequests() {
    }

    /** @param scoreEntry who records match scores (D-029); omitted means {@code MEMBERS} */
    public record CreateLeagueRequest(String name, String description, LeagueVisibility visibility,
                                      ScoreEntry scoreEntry) {
    }

    /** Null fields stay unchanged; an empty description removes it. */
    public record UpdateLeagueRequest(String name, String description, LeagueVisibility visibility,
                                      ScoreEntry scoreEntry) {
    }

    public record ChangeRoleRequest(LeagueRole role) {
    }
}
