package com.lucchetta.core.stats;

import com.lucchetta.core.domain.Player;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Orders players by decision D-013. Positions are never shared: the last criteria always tell two
 * players apart.
 */
public final class RankingCalculator {

    /**
     * A player to rank.
     *
     * @param stats           statistics of the period ({@link PlayerStats#EMPTY} without matches)
     * @param pointsReachedAt when the player reached their current points: the time of the match
     *                        that brought them to it; null without points
     * @param joinedAt        when the player joined the league; null if no longer a member
     */
    public record Candidate(Player player, PlayerStats stats, Instant pointsReachedAt, Instant joinedAt) {
    }

    /** Decision D-013, in order. Missing instants (no points, former member) come last. */
    static final Comparator<Candidate> ORDER = Comparator
            .comparing(Candidate::stats, Comparator.comparingInt(PlayerStats::points).reversed())
            .thenComparing(Candidate::stats, Comparator.comparingInt(PlayerStats::wins).reversed())
            .thenComparing(Candidate::stats, Comparator.comparingInt(PlayerStats::gameDifference).reversed())
            .thenComparing(Candidate::stats, Comparator.comparingInt(PlayerStats::matchesPlayed))
            .thenComparing(Candidate::stats, Comparator.comparingInt(PlayerStats::gamesWon).reversed())
            .thenComparing(Candidate::pointsReachedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Candidate::joinedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(c -> c.player().displayName().value(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(c -> c.player().createdAt())
            .thenComparing(c -> c.player().id());

    private RankingCalculator() {
    }

    /** Every candidate gets a distinct position, 1 to n. */
    public static List<RankingEntry> rank(Collection<Candidate> candidates) {
        List<Candidate> sorted = candidates.stream().sorted(ORDER).toList();
        return IntStream.range(0, sorted.size())
                .mapToObj(i -> new RankingEntry(i + 1, sorted.get(i).player().id(),
                        sorted.get(i).player().displayName().value(), sorted.get(i).stats()))
                .toList();
    }
}
