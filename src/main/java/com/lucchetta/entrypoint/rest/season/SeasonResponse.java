package com.lucchetta.entrypoint.rest.season;

import com.lucchetta.core.domain.SeasonStatus;
import com.lucchetta.core.usecase.season.SeasonView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param playingDays days of the week whose matches count (D-025), Monday first
 */
public record SeasonResponse(UUID id, String name, LocalDate startDate, LocalDate endDate,
                             List<DayOfWeek> playingDays, SeasonStatus status) {

    public static SeasonResponse from(SeasonView view) {
        return new SeasonResponse(view.season().id(), view.season().name(), view.season().startDate(),
                view.season().endDate(), view.season().playingDays().stream().sorted().toList(), view.status());
    }
}
