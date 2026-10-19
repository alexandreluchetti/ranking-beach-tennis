package com.lucchetta.core.usecase.ranking;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.exception.ForbiddenException;
import com.lucchetta.core.stats.PlayerStats;
import com.lucchetta.core.stats.RankingEntry;
import com.lucchetta.core.support.InMemorySeasons;
import com.lucchetta.core.support.TestLeague;
import com.lucchetta.core.usecase.season.QuerySeasons;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class GetSeasonRankingTest {

    private final RankingFixture world = new RankingFixture();
    private final GetSeasonRanking useCase = new GetSeasonRanking(world.access, world.querySeasons, world.matches,
            world.players, world.league.leagues, world.assembler);
    private final AuthenticatedUser caller = RankingFixture.user(world.ana);

    @Test
    void sumsPointsWinsLossesAndGameDifference() {
        world.match(LocalDate.of(2026, 10, 4), 6, 4);   // team A wins: A +9, B +4
        world.match(LocalDate.of(2026, 10, 11), 6, 7);  // team B wins (tie-break): A +6, B +10

        List<RankingEntry> ranking = useCase.current(caller, TestLeague.ID).orElseThrow().entries();

        assertThat(ranking).extracting(RankingEntry::position, RankingEntry::displayName, e -> e.stats().points())
                // Partners tie on everything up to joining the league: the name decides (D-013, criterion 7).
                .containsExactly(tuple(1, "Ana", 15), tuple(2, "Beto", 15), tuple(3, "Caio", 14), tuple(4, "Dani", 14));
        assertThat(entry(ranking, world.ana.id()).stats()).isEqualTo(new PlayerStats(2, 1, 1, 12, 11, 15));
        assertThat(entry(ranking, world.caio.id()).stats()).isEqualTo(new PlayerStats(2, 1, 1, 11, 12, 14));
    }

    @Test
    void equalResultsAreDecidedByWhoReachedThePointsFirst() {
        world.match(LocalDate.of(2026, 10, 4), 6, 0);   // Ana and Beto reach 9 points
        world.match(LocalDate.of(2026, 10, 11), 0, 6);  // Caio and Dani reach 9 points a week later

        assertThat(useCase.current(caller, TestLeague.ID).orElseThrow().entries())
                .extracting(RankingEntry::position, RankingEntry::displayName, e -> e.stats().points())
                .containsExactly(tuple(1, "Ana", 9), tuple(2, "Beto", 9), tuple(3, "Caio", 9), tuple(4, "Dani", 9));
    }

    @Test
    void playersWithoutPointsFollowTheirLeagueEntryBeforeTheirName() {
        world.member("Abel", Instant.parse("2026-10-05T12:00:00Z"));

        assertThat(useCase.current(caller, TestLeague.ID).orElseThrow().entries())
                .extracting(RankingEntry::position, RankingEntry::displayName)
                .containsExactly(tuple(1, "Ana"), tuple(2, "Beto"), tuple(3, "Caio"), tuple(4, "Dani"),
                        tuple(5, "Abel"));
    }

    @Test
    void guestMatchesCountForTheSeasonAndGivePointsToRegisteredPlayers() {
        world.match(LocalDate.of(2026, 10, 4), 6, 2, MatchStatus.VALID, world.anaAndGuestVsCaioAndDani());

        List<RankingEntry> ranking = useCase.current(caller, TestLeague.ID).orElseThrow().entries();

        assertThat(entry(ranking, world.ana.id()).stats()).isEqualTo(new PlayerStats(1, 1, 0, 6, 2, 9));
        assertThat(entry(ranking, world.caio.id()).stats()).isEqualTo(new PlayerStats(1, 0, 1, 2, 6, 2));
    }

    @Test
    void seasonStatisticsCountEveryValidMatchOfTheSeason() {
        world.match(LocalDate.of(2026, 10, 4), 0, 6);   // Ana and Beto lose 0-6
        world.match(LocalDate.of(2026, 10, 5), 6, 2, MatchStatus.VALID, world.anaAndGuestVsCaioAndDani()); // +9
        world.match(LocalDate.of(2026, 5, 10), 6, 4);   // outside any season

        RankingEntry ana = entry(useCase.current(caller, TestLeague.ID).orElseThrow().entries(), world.ana.id());

        assertThat(ana.stats()).isEqualTo(new PlayerStats(2, 1, 1, 6, 8, 9));
    }

    @Test
    void voidedMatchesCountForNothing() {
        var regular = world.match(LocalDate.of(2026, 10, 5), 6, 0);
        world.match(LocalDate.of(2026, 10, 4), 6, 2, MatchStatus.VOIDED, regular.participants());

        RankingEntry ana = entry(useCase.current(caller, TestLeague.ID).orElseThrow().entries(), world.ana.id());

        assertThat(ana.stats()).isEqualTo(new PlayerStats(1, 1, 0, 6, 0, 9));
    }

    @Test
    void onlyMatchesWithinTheSeasonPeriodCount() {
        world.match(LocalDate.of(2026, 9, 30), 6, 0);   // last day of the past season
        world.match(LocalDate.of(2026, 10, 1), 6, 4);   // first day of the active season

        RankingEntry activeAna = entry(useCase.current(caller, TestLeague.ID).orElseThrow().entries(), world.ana.id());
        RankingEntry pastAna = entry(useCase.bySeasonId(caller, TestLeague.ID, world.pastSeason.id()).entries(),
                world.ana.id());

        assertThat(activeAna.stats()).isEqualTo(new PlayerStats(1, 1, 0, 6, 4, 9));
        assertThat(pastAna.stats()).isEqualTo(new PlayerStats(1, 1, 0, 6, 0, 9));
    }

    @Test
    void everyLeagueMemberAppearsInTheActiveSeasonStartingAtZero() {
        Player eva = world.member("Eva", Instant.parse("2026-10-12T12:00:00Z"));

        List<RankingEntry> ranking = useCase.current(caller, TestLeague.ID).orElseThrow().entries();

        assertThat(ranking).hasSize(5);
        assertThat(entry(ranking, eva.id()).stats()).isEqualTo(PlayerStats.EMPTY);
    }

    @Test
    void pastSeasonDoesNotListPlayersWhoJoinedAfterItEnded() {
        world.member("Eva", Instant.parse("2026-10-05T12:00:00Z"));

        assertThat(useCase.bySeasonId(caller, TestLeague.ID, world.pastSeason.id()).entries())
                .extracting(RankingEntry::displayName)
                .containsExactlyInAnyOrder("Ana", "Beto", "Caio", "Dani");
    }

    @Test
    void noActiveSeasonMeansNoCurrentRanking() {
        GetSeasonRanking withoutSeasons = new GetSeasonRanking(
                world.access, new QuerySeasons(world.access, new InMemorySeasons(), RankingFixture.CLOCK),
                world.matches, world.players, world.league.leagues, world.assembler);

        assertThat(withoutSeasons.current(caller, TestLeague.ID)).isEmpty();
    }

    @Test
    void usersWhoAreNotMembersAreNotRanked() {
        world.players.add("Fora", Instant.parse("2026-10-05T12:00:00Z"));

        assertThat(useCase.current(caller, TestLeague.ID).orElseThrow().entries())
                .extracting(RankingEntry::displayName)
                .containsExactlyInAnyOrder("Ana", "Beto", "Caio", "Dani");
    }

    @Test
    void formerMembersStayInTheSeasonsTheyPlayed() {
        world.match(LocalDate.of(2026, 10, 4), 6, 4);
        world.league.leagues.delete(TestLeague.ID, world.dani.id());

        assertThat(entry(useCase.current(caller, TestLeague.ID).orElseThrow().entries(), world.dani.id())
                .stats().matchesPlayed()).isEqualTo(1);
        assertThat(useCase.bySeasonId(caller, TestLeague.ID, world.pastSeason.id()).entries())
                .extracting(RankingEntry::displayName)
                .containsExactlyInAnyOrder("Ana", "Beto", "Caio");
    }

    @Test
    void onlyMembersSeeTheRanking() {
        AuthenticatedUser outsider = RankingFixture.user(world.players.add("Fora"));

        assertThatThrownBy(() -> useCase.current(outsider, TestLeague.ID)).isInstanceOf(ForbiddenException.class);
    }

    private static RankingEntry entry(List<RankingEntry> ranking, UUID playerId) {
        return ranking.stream().filter(e -> e.playerId().equals(playerId)).findFirst().orElseThrow();
    }
}
