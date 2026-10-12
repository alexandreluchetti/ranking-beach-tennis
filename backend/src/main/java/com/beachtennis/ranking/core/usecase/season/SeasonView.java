package com.beachtennis.ranking.core.usecase.season;

import com.beachtennis.ranking.core.domain.Season;
import com.beachtennis.ranking.core.domain.SeasonStatus;

/**
 * A season together with its status on the current business date.
 */
public record SeasonView(Season season, SeasonStatus status) {
}
