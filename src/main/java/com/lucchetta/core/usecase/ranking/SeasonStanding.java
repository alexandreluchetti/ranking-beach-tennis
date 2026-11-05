package com.lucchetta.core.usecase.ranking;

import com.lucchetta.core.stats.PlayerStats;
import com.lucchetta.core.stats.TournamentRecord;
import com.lucchetta.core.usecase.season.SeasonView;

/**
 * A player's place in one season's ranking.
 *
 * @param tournament stages, titles and finals in a daily tournament season (D-028); null otherwise
 */
public record SeasonStanding(SeasonView season, int position, int rankedPlayers, PlayerStats stats,
                             TournamentRecord tournament) {
}
