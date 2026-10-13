package com.lucchetta.core.usecase.match;

import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.PlayerRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Rules that need the outside world (clock, registered players), shared by registration and
 * correction. Rules on the match itself live in the domain objects.
 */
public class MatchValidator {

    static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(10);

    private final PlayerRepository playerRepository;

    public MatchValidator(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public void validate(Match match, Instant now) {
        if (match.playedAt().isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new RuleViolationException("MATCH_IN_FUTURE");
        }
        Set<UUID> playerIds = Set.copyOf(match.playerIds());
        if (playerRepository.findAllByIds(playerIds).size() != playerIds.size()) {
            throw new RuleViolationException("UNKNOWN_PLAYER");
        }
    }
}
