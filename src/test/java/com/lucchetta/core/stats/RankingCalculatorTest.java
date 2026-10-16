package com.lucchetta.core.stats;

import com.lucchetta.core.domain.Player;
import com.lucchetta.core.stats.RankingCalculator.Candidate;
import com.lucchetta.core.support.InMemoryPlayers;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Decision D-013: every criterion in order, and positions are never shared. */
class RankingCalculatorTest {

    private static final Instant T0 = Instant.parse("2026-10-01T12:00:00Z");
    private static final PlayerStats SAME = stats(1, 1, 0, 6, 4, 9);

    private final InMemoryPlayers players = new InMemoryPlayers();
    private final Player ana = players.add("Ana", T0);
    private final Player beto = players.add("Beto", T0);
    private final Player caio = players.add("Caio", T0);

    @Test
    void morePointsFirst() {
        assertOrder(List.of(
                candidate(ana, stats(1, 1, 0, 4, 6, 4)),
                candidate(beto, stats(1, 1, 0, 6, 4, 9))), "Beto", "Ana");
    }

    @Test
    void thenMoreWins() {
        assertOrder(List.of(
                candidate(ana, stats(2, 0, 2, 10, 12, 10)),
                candidate(beto, stats(2, 1, 1, 7, 9, 10))), "Beto", "Ana");
    }

    @Test
    void thenBetterGameDifference() {
        assertOrder(List.of(
                candidate(ana, stats(2, 1, 1, 10, 9, 10)),
                candidate(beto, stats(2, 1, 1, 10, 6, 10))), "Beto", "Ana");
    }

    @Test
    void thenFewerMatchesPlayed() {
        assertOrder(List.of(
                candidate(ana, stats(3, 1, 2, 10, 10, 13)),
                candidate(beto, stats(2, 1, 1, 10, 10, 13))), "Beto", "Ana");
    }

    @Test
    void thenMoreGamesWon() {
        assertOrder(List.of(
                candidate(ana, stats(2, 1, 1, 9, 9, 12)),
                candidate(beto, stats(2, 1, 1, 12, 12, 12))), "Beto", "Ana");
    }

    @Test
    void thenWhoReachedTheCurrentPointsFirst() {
        assertOrder(List.of(
                new Candidate(ana, SAME, T0.plusSeconds(3600), T0),
                new Candidate(beto, SAME, T0, T0.plusSeconds(60))), "Beto", "Ana");
    }

    @Test
    void thenWhoJoinedTheLeagueFirst() {
        assertOrder(List.of(
                new Candidate(ana, SAME, T0, T0.plusSeconds(60)),
                new Candidate(beto, SAME, T0, T0)), "Beto", "Ana");
    }

    @Test
    void formerMembersComeAfterMembersWithEqualResults() {
        assertOrder(List.of(
                new Candidate(ana, SAME, T0, null),
                new Candidate(beto, SAME, T0, T0.plusSeconds(60))), "Beto", "Ana");
    }

    @Test
    void thenNameAndFinallyRegistration() {
        Player otherAna = players.add("ana", T0.minusSeconds(60));

        assertOrder(List.of(
                new Candidate(beto, SAME, T0, T0),
                new Candidate(ana, SAME, T0, T0),
                new Candidate(otherAna, SAME, T0, T0)), "ana", "Ana", "Beto");
    }

    @Test
    void positionsAreNeverShared() {
        Player dani = players.add("Dani", T0);

        List<RankingEntry> ranking = RankingCalculator.rank(List.of(
                new Candidate(dani, PlayerStats.EMPTY, null, T0),
                new Candidate(caio, SAME, T0, T0),
                new Candidate(ana, stats(1, 1, 0, 6, 0, 9), T0, T0),
                new Candidate(beto, SAME, T0, T0)));

        assertThat(ranking).extracting(RankingEntry::position, RankingEntry::displayName)
                .containsExactly(tuple(1, "Ana"), tuple(2, "Beto"), tuple(3, "Caio"), tuple(4, "Dani"));
    }

    @Test
    void playersWithoutPointsFollowTheirLeagueEntry() {
        List<RankingEntry> ranking = RankingCalculator.rank(List.of(
                new Candidate(ana, PlayerStats.EMPTY, null, T0.plusSeconds(120)),
                new Candidate(caio, PlayerStats.EMPTY, null, T0),
                new Candidate(beto, PlayerStats.EMPTY, null, T0.plusSeconds(60))));

        assertThat(ranking).extracting(RankingEntry::position, RankingEntry::displayName)
                .containsExactly(tuple(1, "Caio"), tuple(2, "Beto"), tuple(3, "Ana"));
    }

    private static void assertOrder(List<Candidate> candidates, String... names) {
        assertThat(RankingCalculator.rank(candidates)).extracting(RankingEntry::displayName).containsExactly(names);
    }

    private static Candidate candidate(Player player, PlayerStats stats) {
        return new Candidate(player, stats, T0, T0);
    }

    private static PlayerStats stats(int matches, int wins, int losses, int gamesWon, int gamesLost, int points) {
        return new PlayerStats(matches, wins, losses, gamesWon, gamesLost, points);
    }
}
