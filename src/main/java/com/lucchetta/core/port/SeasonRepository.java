package com.lucchetta.core.port;

import com.lucchetta.core.domain.Season;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonRepository {

    /** Seasons of the league, most recent start date first. */
    List<Season> findAll(UUID leagueId);

    Optional<Season> findById(UUID id);

    /** The league's season whose period contains the given date, if any. */
    Optional<Season> findContaining(UUID leagueId, LocalDate date);

    /**
     * Whether any season of the league other than {@code excludedId} overlaps the given inclusive
     * period.
     */
    boolean existsOverlapping(UUID leagueId, LocalDate startDate, LocalDate endDate, UUID excludedId);

    /**
     * @throws com.lucchetta.core.exception.ConflictException if the period overlaps
     *                                                                   another season of the league
     */
    Season insert(Season season);

    /**
     * @throws com.lucchetta.core.exception.ConflictException if the period overlaps
     *                                                                   another season of the league
     */
    Season update(Season season);
}
