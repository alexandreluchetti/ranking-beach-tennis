package com.beachtennis.ranking.core.usecase.match;

import com.beachtennis.ranking.core.exception.NotFoundException;
import com.beachtennis.ranking.core.port.MatchRepository;

import java.util.UUID;

public class QueryMatch {

    private final MatchRepository matchRepository;
    private final MatchDetailsAssembler assembler;

    public QueryMatch(MatchRepository matchRepository, MatchDetailsAssembler assembler) {
        this.matchRepository = matchRepository;
        this.assembler = assembler;
    }

    public MatchDetails byId(UUID id) {
        return matchRepository.findById(id)
                .map(assembler::assemble)
                .orElseThrow(() -> new NotFoundException("MATCH_NOT_FOUND"));
    }
}
