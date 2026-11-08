package com.lucchetta.core.usecase.match;

import com.lucchetta.core.port.LeagueMatchCache;
import com.lucchetta.core.port.MatchRepository;

import java.util.List;
import java.util.UUID;

/**
 * Every match of a league (any status, most recent first) with season, eligibility and points,
 * including those of other leagues that count in it (D-031): the input of rankings and profiles.
 * Served from the {@link LeagueMatchCache} when possible.
 */
public class LeagueMatches {

    private final MatchRepository matchRepository;
    private final MatchDetailsAssembler assembler;
    private final SharedMatches sharedMatches;
    private final LeagueMatchCache cache;

    public LeagueMatches(MatchRepository matchRepository, MatchDetailsAssembler assembler, SharedMatches sharedMatches,
                         LeagueMatchCache cache) {
        this.matchRepository = matchRepository;
        this.assembler = assembler;
        this.sharedMatches = sharedMatches;
        this.cache = cache;
    }

    public List<MatchDetails> all(UUID leagueId) {
        return cache.get(leagueId, () -> List.copyOf(SharedMatches.merged(
                assembler.assembleAll(matchRepository.findPlayed(leagueId, null, null, null)),
                sharedMatches.into(leagueId, null, null, null))));
    }
}
