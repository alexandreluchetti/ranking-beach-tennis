package com.lucchetta.core.stats;

import java.util.UUID;

/**
 * @param position   1-based and never shared (decision D-013)
 * @param tournament stages, titles and finals in a daily tournament season (D-028); null for any
 *                   other ranking type
 */
public record RankingEntry(int position, UUID playerId, String displayName, PlayerStats stats,
                           TournamentRecord tournament) {
}
