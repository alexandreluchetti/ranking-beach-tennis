package com.lucchetta.core.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Playing days a season had before a change made while it was active (D-025). They applied from
 * the day after the previous period (or from the season start) up to {@code lastDay}, inclusive.
 */
public record PastPlayingDays(LocalDate lastDay, Set<DayOfWeek> playingDays) {

    public PastPlayingDays {
        Objects.requireNonNull(lastDay, "lastDay");
        if (playingDays == null || playingDays.isEmpty()) {
            throw new IllegalArgumentException("playingDays must not be empty");
        }
        playingDays = Collections.unmodifiableSet(EnumSet.copyOf(playingDays));
    }
}
