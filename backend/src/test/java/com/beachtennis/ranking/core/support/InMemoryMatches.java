package com.beachtennis.ranking.core.support;

import com.beachtennis.ranking.core.domain.match.Match;
import com.beachtennis.ranking.core.domain.match.MatchStatus;
import com.beachtennis.ranking.core.exception.ConflictException;
import com.beachtennis.ranking.core.port.MatchRepository;

import java.time.Instant;
import java.util.Comparator;
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
    public Match update(Match match) {
        Match stored = matches.get(match.id());
        if (stored == null || stored.version() != match.version()) {
            throw new ConflictException("MATCH_MODIFIED");
        }
        Match saved = new Match(match.id(), match.playedAt(), match.score(), match.participants(), match.status(),
                match.registeredBy(), match.registeredAt(), match.updatedAt(), match.voiding(), match.version() + 1);
        matches.put(saved.id(), saved);
        return saved;
    }

    /** Test helper: stores a match as-is (any status). */
    public Match add(Match match) {
        matches.put(match.id(), match);
        return match;
    }

    @Override
    public List<Match> findPlayed(Instant from, Instant to, UUID playerId) {
        return matches.values().stream()
                .filter(m -> from == null || !m.playedAt().isBefore(from))
                .filter(m -> to == null || m.playedAt().isBefore(to))
                .filter(m -> playerId == null || m.participants().stream().anyMatch(p -> playerId.equals(p.playerId())))
                .sorted(Comparator.comparing(Match::playedAt).reversed())
                .toList();
    }

    @Override
    public List<Match> findValidPlayedBetween(Instant from, Instant to) {
        return matches.values().stream()
                .filter(m -> m.status() == MatchStatus.VALID)
                .filter(m -> !m.playedAt().isBefore(from) && m.playedAt().isBefore(to))
                .toList();
    }
}
