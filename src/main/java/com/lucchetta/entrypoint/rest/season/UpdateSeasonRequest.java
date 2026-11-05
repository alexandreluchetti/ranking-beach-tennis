package com.lucchetta.entrypoint.rest.season;

import com.lucchetta.core.domain.RankingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Partial update: omitted fields keep their current value. The ranking type and the placement
 * points may change only while the season is planned (D-026).
 */
public record UpdateSeasonRequest(String name, LocalDate startDate, LocalDate endDate,
                                  Set<@NotNull DayOfWeek> playingDays, RankingType rankingType,
                                  @Valid PlacementPointsDto placementPoints) {
}
