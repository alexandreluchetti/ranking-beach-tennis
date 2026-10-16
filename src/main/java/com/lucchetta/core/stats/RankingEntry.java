package com.lucchetta.core.stats;

import java.util.UUID;

/**
 * @param position 1-based and never shared (decision D-013)
 */
public record RankingEntry(int position, UUID playerId, String displayName, PlayerStats stats) {
}
