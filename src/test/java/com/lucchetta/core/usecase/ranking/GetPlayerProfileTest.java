package com.lucchetta.core.usecase.ranking;

import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.stats.PlayerStats;
import com.lucchetta.core.support.InMemorySeasons;
import com.lucchetta.core.usecase.season.QuerySeasons;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetPlayerProfileTest {

    private final RankingFixture world = new RankingFixture();
    private final GetPlayerProfile useCase = new GetPlayerProfile(world.players, world.matches, world.querySeasons,
            world.assembler);

    @Test
    void combinesCurrentSeasonCareerAndPastSeasons() {
        world.match(LocalDate.of(2026, 9, 6), 6, 0);    // past season: Ana +9
        world.match(LocalDate.of(2026, 10, 4), 4, 6);   // active season: Ana +4 (loss)
        world.match(LocalDate.of(2026, 5, 10), 6, 3);   // outside any season: stats only
        world.match(LocalDate.of(2026, 10, 11), 6, 2, MatchStatus.VALID, world.anaAndGuestVsCaioAndDani()); // guest
        world.match(LocalDate.of(2026, 10, 10), 6, 1, MatchStatus.VOIDED,
                world.match(LocalDate.of(2026, 3, 1), 6, 4).participants()); // voided; the 6-4 is outside seasons

        GetPlayerProfile.PlayerProfile profile = useCase.execute(world.ana.id());

        assertThat(profile.player()).isEqualTo(world.ana);
        assertThat(profile.currentSeason().season().season()).isEqualTo(world.activeSeason);
        assertThat(profile.currentSeason().stats()).isEqualTo(new PlayerStats(2, 1, 1, 10, 8, 4));
        assertThat(profile.currentSeason().position()).isEqualTo(3);
        assertThat(profile.currentSeason().rankedPlayers()).isEqualTo(4);

        assertThat(profile.pastSeasons()).singleElement().satisfies(standing -> {
            assertThat(standing.season().season()).isEqualTo(world.pastSeason);
            assertThat(standing.position()).isEqualTo(1);
            assertThat(standing.stats().points()).isEqualTo(9);
        });

        // Career: 6-0, 4-6, 6-3, 6-2 (guest) and 6-4; the voided match is excluded. Points: 9 + 4.
        assertThat(profile.career()).isEqualTo(new PlayerStats(5, 4, 1, 28, 15, 13));
    }

    @Test
    void noCurrentStandingWithoutActiveSeason() {
        RankingFixture noActive = new RankingFixture();
        GetPlayerProfile withoutActive = new GetPlayerProfile(noActive.players, noActive.matches,
                new QuerySeasons(new InMemorySeasons(), RankingFixture.CLOCK),
                noActive.assembler);

        GetPlayerProfile.PlayerProfile profile = withoutActive.execute(noActive.ana.id());

        assertThat(profile.currentSeason()).isNull();
        assertThat(profile.pastSeasons()).isEmpty();
        assertThat(profile.career()).isEqualTo(PlayerStats.EMPTY);
    }

    @Test
    void unknownPlayer() {
        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("PLAYER_NOT_FOUND");
    }
}
