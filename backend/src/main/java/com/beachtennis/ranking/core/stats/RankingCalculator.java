package com.beachtennis.ranking.core.stats;

import com.beachtennis.ranking.core.domain.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orders players by decision D-013: points, wins, game difference, fewer matches played; players
 * still tied share the position.
 */
public final class RankingCalculator {

    /** D-013 criteria only; two players equal under it share a position. */
    static final Comparator<PlayerStats> ORDER = Comparator
            .comparingInt(PlayerStats::points).reversed()
            .thenComparing(Comparator.comparingInt(PlayerStats::wins).reversed())
            .thenComparing(Comparator.comparingInt(PlayerStats::gameDifference).reversed())
            .thenComparingInt(PlayerStats::matchesPlayed);

    private RankingCalculator() {
    }

    /**
     * @param members players listed in the ranking, with zeroed stats when they have no matches
     * @param stats   statistics of the period, by player id
     */
    public static List<RankingEntry> rank(Collection<Player> members, Map<UUID, PlayerStats> stats) {
        record Row(Player player, PlayerStats stats) {
        }
        List<Row> rows = members.stream()
                .map(p -> new Row(p, stats.getOrDefault(p.id(), PlayerStats.EMPTY)))
                .sorted(Comparator.comparing(Row::stats, ORDER)
                        // Display order inside a shared position only; not a tie-break.
                        .thenComparing(r -> r.player().displayName().value(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<RankingEntry> entries = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int position = i > 0 && ORDER.compare(rows.get(i - 1).stats(), row.stats()) == 0
                    ? entries.get(i - 1).position()
                    : i + 1;
            entries.add(new RankingEntry(position, row.player().id(), row.player().displayName().value(), row.stats()));
        }
        return entries;
    }
}
