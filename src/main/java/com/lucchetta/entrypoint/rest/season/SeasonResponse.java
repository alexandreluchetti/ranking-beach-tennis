package com.lucchetta.entrypoint.rest.season;

import com.lucchetta.core.domain.SeasonStatus;
import com.lucchetta.core.usecase.season.SeasonView;

import java.time.LocalDate;
import java.util.UUID;

public record SeasonResponse(UUID id, String name, LocalDate startDate, LocalDate endDate, SeasonStatus status) {

    public static SeasonResponse from(SeasonView view) {
        return new SeasonResponse(view.season().id(), view.season().name(), view.season().startDate(),
                view.season().endDate(), view.status());
    }
}
