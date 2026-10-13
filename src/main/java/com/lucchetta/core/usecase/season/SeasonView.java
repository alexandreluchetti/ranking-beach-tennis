package com.lucchetta.core.usecase.season;

import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.SeasonStatus;

/**
 * A season together with its status on the current business date.
 */
public record SeasonView(Season season, SeasonStatus status) {
}
