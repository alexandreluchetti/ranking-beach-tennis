package com.lucchetta.entrypoint.rest.season;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Partial update: omitted fields keep their current value.
 */
public record UpdateSeasonRequest(String name, LocalDate startDate, LocalDate endDate,
                                  Set<@NotNull DayOfWeek> playingDays) {
}
