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

    /**
     * Matches of any status, most recent first.
     *
     * @param from     inclusive lower bound, or null
     * @param to       exclusive upper bound, or null
     * @param playerId only matches this registered player took part in, or null
     */
    List<Match> findPlayed(Instant from, Instant to, UUID playerId);
}
