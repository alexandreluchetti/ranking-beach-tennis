package com.beachtennis.ranking.core.usecase.ranking;

import com.beachtennis.ranking.core.stats.PlayerStats;
import com.beachtennis.ranking.core.usecase.season.SeasonView;

/**
 * A player's place in one season's ranking.
 */
public record SeasonStanding(SeasonView season, int position, int rankedPlayers, PlayerStats stats) {
}
