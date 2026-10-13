package com.beachtennis.ranking.core.port;

import com.beachtennis.ranking.core.domain.match.Match;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository {

    Optional<Match> findById(UUID id);

    /**
     * Inserts the match unless one with the same id already exists.
     *
     * @return true if inserted, false if the id was already taken
     */
    boolean insertIfAbsent(Match match);

    /** Valid (not voided) matches played in {@code [from, to)}. */
    List<Match> findValidPlayedBetween(Instant from, Instant to);
}
