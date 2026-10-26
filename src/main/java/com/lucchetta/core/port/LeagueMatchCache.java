package com.lucchetta.core.port;

import com.lucchetta.core.usecase.match.MatchDetails;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Keeps the assembled matches of each league between requests. Only a copy of derived data
 * (D-007): it may be emptied at any time, and the adapters writing matches, seasons and player
 * names evict it, so readers never see a change undone.
 */
public interface LeagueMatchCache {

    /** No caching: every call loads the matches again. */
    LeagueMatchCache NONE = (leagueId, loader) -> loader.get();

    /**
     * @param loader loads the league's matches when they are not cached
     */
    List<MatchDetails> get(UUID leagueId, Supplier<List<MatchDetails>> loader);
}
