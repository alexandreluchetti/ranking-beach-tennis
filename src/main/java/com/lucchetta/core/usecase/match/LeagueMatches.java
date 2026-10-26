package com.lucchetta.core.usecase.match;

import com.lucchetta.core.port.LeagueMatchCache;
import com.lucchetta.core.port.MatchRepository;

import java.util.List;
import java.util.UUID;

/**
 * Every match of a league (any status, most recent first) with season, eligibility and points,
 * the input of rankings and profiles. Served from the {@link LeagueMatchCache} when possible.
 */
public class LeagueMatches {

    private final MatchRepository matchRepository;
    private final MatchDetailsAssembler assembler;
    private final LeagueMatchCache cache;

    public LeagueMatches(MatchRepository matchRepository, MatchDetailsAssembler assembler, LeagueMatchCache cache) {
        this.matchRepository = matchRepository;
        this.assembler = assembler;
        this.cache = cache;
    }

    public List<MatchDetails> all(UUID leagueId) {
        return cache.get(leagueId,
                () -> List.copyOf(assembler.assembleAll(matchRepository.findPlayed(leagueId, null, null, null))));
    }
}
