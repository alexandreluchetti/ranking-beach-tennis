package com.lucchetta.entrypoint.rest.season;

import java.time.LocalDate;

/**
 * Partial update: omitted fields keep their current value.
 */
public record UpdateSeasonRequest(String name, LocalDate startDate, LocalDate endDate) {
}
