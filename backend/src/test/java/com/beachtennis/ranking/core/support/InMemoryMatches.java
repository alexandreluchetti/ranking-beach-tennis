package com.beachtennis.ranking.core.support;

import com.beachtennis.ranking.core.domain.match.Match;
import com.beachtennis.ranking.core.domain.match.MatchStatus;
import com.beachtennis.ranking.core.port.MatchRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryMatches implements MatchRepository {

    private final Map<UUID, Match> matches = new LinkedHashMap<>();
    private int insertCalls;

    public int count() {
        return matches.size();
    }

    public int insertCalls() {
        return insertCalls;
    }

    @Override
    public Optional<Match> findById(UUID id) {
        return Optional.ofNullable(matches.get(id));
    }

    @Override
    public boolean insertIfAbsent(Match match) {
        insertCalls++;
        return matches.putIfAbsent(match.id(), match) == null;
    }

    @Override
    public List<Match> findValidPlayedBetween(Instant from, Instant to) {
        return matches.values().stream()
                .filter(m -> m.status() == MatchStatus.VALID)
                .filter(m -> !m.playedAt().isBefore(from) && m.playedAt().isBefore(to))
                .toList();
    }
}
