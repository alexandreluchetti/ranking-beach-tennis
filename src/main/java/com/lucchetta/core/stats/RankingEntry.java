package com.lucchetta.core.stats;

import java.util.UUID;

/**
 * @param position 1-based; tied players share the same position and the next one is skipped
 *                 (1, 1, 3)
 */
public record RankingEntry(int position, UUID playerId, String displayName, PlayerStats stats) {
}
