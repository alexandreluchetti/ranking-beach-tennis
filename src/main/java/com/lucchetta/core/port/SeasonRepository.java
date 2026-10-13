package com.lucchetta.core.port;

import com.lucchetta.core.domain.Season;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonRepository {

    /** All seasons, most recent start date first. */
    List<Season> findAll();

    Optional<Season> findById(UUID id);

    /** The season whose period contains the given date, if any. */
    Optional<Season> findContaining(LocalDate date);

    /** Whether any season other than {@code excludedId} overlaps the given inclusive period. */
    boolean existsOverlapping(LocalDate startDate, LocalDate endDate, UUID excludedId);

    /**
     * @throws com.lucchetta.core.exception.ConflictException if the period overlaps
     *                                                                   another season
     */
    Season insert(Season season);

    /**
     * @throws com.lucchetta.core.exception.ConflictException if the period overlaps
     *                                                                   another season
     */
    Season update(Season season);
}
