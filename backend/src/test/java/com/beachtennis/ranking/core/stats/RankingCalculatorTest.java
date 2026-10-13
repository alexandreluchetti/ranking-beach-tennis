package com.beachtennis.ranking.core.stats;

import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.support.InMemoryPlayers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Decision D-013. */
class RankingCalculatorTest {

    private final InMemoryPlayers players = new InMemoryPlayers();
    private final Player ana = players.add("Ana");
    private final Player beto = players.add("Beto");
    private final Player caio = players.add("Caio");

    @Test
    void morePointsFirst() {
        List<RankingEntry> ranking = RankingCalculator.rank(List.of(ana, beto), Map.of(
                ana.id(), stats(1, 1, 0, 4, 6, 4),
                beto.id(), stats(1, 1, 0, 6, 4, 9)));

        assertThat(ranking).extracting(RankingEntry::position, RankingEntry::displayName)
                .containsExactly(tuple(1, "Beto"), tuple(2, "Ana"));
    }

    @Test
    void thenMoreWins() {
        List<RankingEntry> ranking = RankingCalculator.rank(List.of(ana, beto), Map.of(
                ana.id(), stats(2, 0, 2, 10, 12, 10),
                beto.id(), stats(2, 1, 1, 7, 9, 10)));

        assertThat(ranking).extracting(RankingEntry::displayName).containsExactly("Beto", "Ana");
    }

    @Test
    void thenBetterGameDifference() {
        List<RankingEntry> ranking = RankingCalculator.rank(List.of(ana, beto), Map.of(
                ana.id(), stats(2, 1, 1, 10, 9, 10),
                beto.id(), stats(2, 1, 1, 10, 6, 10)));

        assertThat(ranking).extracting(RankingEntry::displayName).containsExactly("Beto", "Ana");
    }

    @Test
    void thenFewerMatchesPlayed() {
        List<RankingEntry> ranking = RankingCalculator.rank(List.of(ana, beto), Map.of(
                ana.id(), stats(3, 1, 2, 10, 10, 13),
                beto.id(), stats(2, 1, 1, 10, 10, 13)));

        assertThat(ranking).extracting(RankingEntry::displayName).containsExactly("Beto", "Ana");
    }

    @Test
    void fullyTiedPlayersSharePositionAndNextPositionIsSkipped() {
        Player dani = players.add("Dani");
        PlayerStats same = stats(1, 1, 0, 6, 4, 9);

        List<RankingEntry> ranking = RankingCalculator.rank(List.of(dani, caio, ana, beto), Map.of(
                ana.id(), stats(1, 1, 0, 6, 0, 9),
                beto.id(), same,
                caio.id(), same));

        assertThat(ranking).extracting(RankingEntry::position, RankingEntry::displayName)
                .containsExactly(tuple(1, "Ana"), tuple(2, "Beto"), tuple(2, "Caio"), tuple(4, "Dani"));
    }

    @Test
    void playersWithoutMatchesAppearWithZeroAndShareLastPosition() {
        List<RankingEntry> ranking = RankingCalculator.rank(List.of(caio, beto, ana), Map.of(
                ana.id(), stats(1, 0, 1, 4, 6, 4)));

        assertThat(ranking).extracting(RankingEntry::position, RankingEntry::displayName, e -> e.stats().points())
                .containsExactly(tuple(1, "Ana", 4), tuple(2, "Beto", 0), tuple(2, "Caio", 0));
    }

    private static PlayerStats stats(int matches, int wins, int losses, int gamesWon, int gamesLost, int points) {
        return new PlayerStats(matches, wins, losses, gamesWon, gamesLost, points);
    }
}
