package com.lucchetta.core.stats;

import com.lucchetta.core.domain.Player;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Orders players by decision D-013, or by D-028 in a daily tournament season. Positions are never
 * shared: the last criteria always tell two players apart.
 */
public final class RankingCalculator {

    /**
     * A player to rank.
     *
     * @param stats           statistics of the period ({@link PlayerStats#EMPTY} without matches)
     * @param pointsReachedAt when the player reached their current points: the time of the match
     *                        that brought them to it; null without points
     * @param joinedAt        when the player joined the league; null if no longer a member
     * @param tournament      stages, titles and finals in a daily tournament season; null for any
     *                        other ranking type
     */
    public record Candidate(Player player, PlayerStats stats, Instant pointsReachedAt, Instant joinedAt,
                            TournamentRecord tournament) {

        public Candidate(Player player, PlayerStats stats, Instant pointsReachedAt, Instant joinedAt) {
            this(player, stats, pointsReachedAt, joinedAt, null);
        }
    }

    /** Criteria shared by every ranking type after its own ones (D-013, 4 to 7). */
    private static final Comparator<Candidate> COMMON_TAIL = Comparator
            .comparing(Candidate::stats, Comparator.comparingInt(PlayerStats::matchesPlayed))
            .thenComparing(Candidate::stats, Comparator.comparingInt(PlayerStats::gamesWon).reversed())
            .thenComparing(Candidate::pointsReachedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Candidate::joinedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(c -> c.player().displayName().value(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(c -> c.player().createdAt())
            .thenComparing(c -> c.player().id());

    /** Decision D-013, in order. Missing instants (no points, former member) come last. */
    static final Comparator<Candidate> ORDER = Comparator
            .comparing(Candidate::stats, Comparator.comparingInt(PlayerStats::points).reversed())
            .thenComparing(Candidate::stats, Comparator.comparingInt(PlayerStats::wins).reversed())
            .thenComparing(Candidate::stats, Comparator.comparingInt(PlayerStats::gameDifference).reversed())
            .thenComparing(COMMON_TAIL);

    /**
     * Daily tournament (D-028): points, titles, finals, game difference; then the remaining
     * criteria of D-013.
     */
    static final Comparator<Candidate> TOURNAMENT_ORDER = Comparator
            .comparing(Candidate::stats, Comparator.comparingInt(PlayerStats::points).reversed())
            .thenComparing(Candidate::tournament, Comparator.comparingInt(TournamentRecord::titles).reversed())
            .thenComparing(Candidate::tournament, Comparator.comparingInt(TournamentRecord::finals).reversed())
            .thenComparing(Candidate::stats, Comparator.comparingInt(PlayerStats::gameDifference).reversed())
            .thenComparing(COMMON_TAIL);

    private RankingCalculator() {
    }

    /** Every candidate gets a distinct position, 1 to n. */
    public static List<RankingEntry> rank(Collection<Candidate> candidates) {
        return rank(candidates, false);
    }

    /**
     * @param dailyTournament order by D-028; every candidate then carries a tournament record
     */
    public static List<RankingEntry> rank(Collection<Candidate> candidates, boolean dailyTournament) {
        List<Candidate> sorted = candidates.stream().sorted(dailyTournament ? TOURNAMENT_ORDER : ORDER).toList();
        return IntStream.range(0, sorted.size())
                .mapToObj(i -> new RankingEntry(i + 1, sorted.get(i).player().id(),
                        sorted.get(i).player().displayName().value(), sorted.get(i).stats(),
                        sorted.get(i).tournament()))
                .toList();
    }
}
