package com.lucchetta.core.usecase.player;

import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.match.Score;
import com.lucchetta.core.stats.PlayerStats;
import com.lucchetta.core.usecase.ranking.RankingFixture;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Global statistics of the caller over every league (decision D-022). */
class GetMyStatisticsTest {

    private final RankingFixture world = new RankingFixture();
    private final GetMyStatistics useCase = new GetMyStatistics(
            new GetOrCreateCurrentPlayer(world.players, RankingFixture.CLOCK), world.matches, world.assembler);

    @Test
    void sumsEveryValidMatchOfEveryLeague() {
        world.match(LocalDate.of(2026, 10, 4), 6, 4);                    // Ana wins 6-4 (league A)
        world.match(LocalDate.of(2026, 5, 10), 3, 6);                    // Ana loses 3-6, outside seasons
        world.match(LocalDate.of(2026, 10, 11), 6, 2, MatchStatus.VALID, world.anaAndGuestVsCaioAndDani());
        world.match(LocalDate.of(2026, 10, 12), 6, 0, MatchStatus.VOIDED,
                world.match(LocalDate.of(2026, 9, 1), 6, 1).participants()); // voided ignored; 6-1 counts
        Match elsewhere = world.match(LocalDate.of(2026, 10, 5), 4, 6);
        world.matches.add(new Match(elsewhere.id(), UUID.randomUUID(), elsewhere.playedAt(),
                new Score(MatchFormat.STANDARD, 4, 6, null, null), elsewhere.participants(), MatchStatus.VALID,
                elsewhere.registeredBy(), elsewhere.registeredAt(), elsewhere.updatedAt())); // another league

        PlayerStats stats = useCase.execute(RankingFixture.user(world.ana));

        // Wins: 6-4, 6-2, 6-1; losses: 3-6, 4-6 (other league).
        assertThat(stats.matchesPlayed()).isEqualTo(5);
        assertThat(stats.wins()).isEqualTo(3);
        assertThat(stats.losses()).isEqualTo(2);
        assertThat(stats.gamesWon()).isEqualTo(25);
        assertThat(stats.gamesLost()).isEqualTo(19);
        assertThat(stats.gameDifference()).isEqualTo(6);
        assertThat(stats.winRate()).isEqualTo(60);
    }

    @Test
    void onlyTheCallersOwnMatchesCount() {
        world.match(LocalDate.of(2026, 10, 4), 6, 4);

        PlayerStats caio = useCase.execute(RankingFixture.user(world.caio));

        assertThat(caio.wins()).isZero();
        assertThat(caio.losses()).isEqualTo(1);
        assertThat(caio.winRate()).isZero();
    }

    @Test
    void noMatchesMeansNoWinRate() {
        assertThat(useCase.execute(RankingFixture.user(world.ana))).isEqualTo(PlayerStats.EMPTY);
        assertThat(useCase.execute(RankingFixture.user(world.ana)).winRate()).isNull();
    }
}
