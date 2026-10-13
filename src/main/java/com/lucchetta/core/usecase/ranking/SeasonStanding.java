package com.lucchetta.core.usecase.ranking;

import com.lucchetta.core.stats.PlayerStats;
import com.lucchetta.core.usecase.season.SeasonView;

/**
 * A player's place in one season's ranking.
 */
public record SeasonStanding(SeasonView season, int position, int rankedPlayers, PlayerStats stats) {
}
