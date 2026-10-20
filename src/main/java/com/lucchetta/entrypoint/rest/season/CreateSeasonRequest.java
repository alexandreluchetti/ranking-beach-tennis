package com.lucchetta.entrypoint.rest.season;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * @param playingDays days of the week whose matches count (D-025), e.g. {@code ["TUESDAY", "THURSDAY"]};
 *                    omitted means every day (app published before D-025)
 */
public record CreateSeasonRequest(@NotNull String name, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                  Set<@NotNull DayOfWeek> playingDays) {
}
