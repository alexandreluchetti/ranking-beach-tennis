package com.lucchetta.entrypoint.rest.season;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateSeasonRequest(@NotNull String name, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {
}
