package com.lucchetta.core.port;

import com.lucchetta.core.domain.match.Match;

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

    /**
     * Saves a changed match if it is still at {@code match.version()}.
     *
     * @return the stored match, with its version incremented
     * @throws com.lucchetta.core.exception.ConflictException MATCH_MODIFIED when someone
     *                                                                   else changed it first
     */
    Match update(Match match);

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
