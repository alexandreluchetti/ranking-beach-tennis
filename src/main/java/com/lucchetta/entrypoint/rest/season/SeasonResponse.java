package com.lucchetta.entrypoint.rest.season;

import com.lucchetta.core.domain.RankingType;
import com.lucchetta.core.domain.SeasonStatus;
import com.lucchetta.core.usecase.season.SeasonView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param playingDays     days of the week whose matches count (D-025), Monday first
 * @param rankingType     how points are given (D-026)
 * @param placementPoints points per placement of a daily tournament (D-028); null for other types
 */
public record SeasonResponse(UUID id, String name, LocalDate startDate, LocalDate endDate,
                             List<DayOfWeek> playingDays, RankingType rankingType,
                             PlacementPointsDto placementPoints, SeasonStatus status) {

    public static SeasonResponse from(SeasonView view) {
        return new SeasonResponse(view.season().id(), view.season().name(), view.season().startDate(),
                view.season().endDate(), view.season().playingDays().stream().sorted().toList(),
                view.season().rankingType(), PlacementPointsDto.from(view.season().placementPoints()), view.status());
    }
}
