package com.beachtennis.ranking.entrypoint.rest.season;

import com.beachtennis.ranking.core.domain.SeasonStatus;
import com.beachtennis.ranking.core.usecase.season.SeasonView;

import java.time.LocalDate;
import java.util.UUID;

public record SeasonResponse(UUID id, String name, LocalDate startDate, LocalDate endDate, SeasonStatus status) {

    public static SeasonResponse from(SeasonView view) {
        return new SeasonResponse(view.season().id(), view.season().name(), view.season().startDate(),
                view.season().endDate(), view.status());
    }
}
