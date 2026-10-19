package com.lucchetta.core.usecase.match;

import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.support.TestLeague;
import com.lucchetta.core.usecase.ranking.RankingFixture;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SearchMatchesTest {

    private final RankingFixture world = new RankingFixture();
    private final SearchMatches useCase = new SearchMatches(world.access, world.matches, world.querySeasons,
            world.assembler);

    private final Match pastSeason = world.match(LocalDate.of(2026, 9, 6), 6, 0);
    private final Match counted = world.match(LocalDate.of(2026, 10, 4), 6, 4);
    private final Match guest = world.match(LocalDate.of(2026, 10, 5), 6, 2, MatchStatus.VALID,
            world.anaAndGuestVsCaioAndDani());
    private final Match voided = world.match(LocalDate.of(2026, 10, 6), 6, 1, MatchStatus.VOIDED,
            counted.participants());
    private final Match outsideSeasons = world.match(LocalDate.of(2026, 5, 10), 6, 3);

    @Test
    void returnsEverythingMostRecentFirstByDefault() {
        assertThat(ids(search(null, null, null, null, Set.of(), 0, 20)))
                .containsExactly(voided.id(), guest.id(), counted.id(), pastSeason.id(), outsideSeasons.id());
    }

    @Test
    void filtersBySeasonPeriod() {
        assertThat(ids(search(world.activeSeason.id(), null, null, null, Set.of(), 0, 20)))
                .containsExactly(voided.id(), guest.id(), counted.id());
    }

    @Test
    void filtersByOutcomeKeepingVoidedSeparateFromNotCounted() {
        assertThat(ids(search(null, null, null, null, Set.of(MatchOutcome.COUNTED), 0, 20)))
                .containsExactly(guest.id(), counted.id(), pastSeason.id()); // guests do not matter (D-004)
        assertThat(ids(search(null, null, null, null, Set.of(MatchOutcome.NOT_COUNTED), 0, 20)))
                .containsExactly(outsideSeasons.id());
        assertThat(ids(search(null, null, null, null, Set.of(MatchOutcome.VOIDED), 0, 20)))
                .containsExactly(voided.id());
    }

    @Test
    void combinesPlayerDatesAndOutcome() {
        assertThat(ids(search(null, world.beto.id(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31),
                EnumSet.of(MatchOutcome.COUNTED, MatchOutcome.VOIDED), 0, 20)))
                .containsExactly(voided.id(), counted.id(), pastSeason.id());
    }

    @Test
    void datesNarrowTheSeasonPeriodAndEmptyRangesReturnNothing() {
        assertThat(ids(search(world.activeSeason.id(), null, LocalDate.of(2026, 10, 5), null, Set.of(), 0, 20)))
                .containsExactly(voided.id(), guest.id());
        assertThat(search(world.pastSeason.id(), null, LocalDate.of(2026, 10, 1), null, Set.of(), 0, 20).items())
                .isEmpty();
    }

    @Test
    void paginates() {
        SearchMatches.Page first = search(null, null, null, null, Set.of(), 0, 2);
        SearchMatches.Page last = search(null, null, null, null, Set.of(), 2, 2);

        assertThat(ids(first)).containsExactly(voided.id(), guest.id());
        assertThat(first.hasNext()).isTrue();
        assertThat(ids(last)).containsExactly(outsideSeasons.id());
        assertThat(last.hasNext()).isFalse();
    }

    @Test
    void itemsCarryEligibilityAndPoints() {
        MatchDetails details = search(null, null, null, null, Set.of(MatchOutcome.COUNTED), 0, 1).items().getFirst();

        assertThat(details.match().id()).isEqualTo(guest.id());
        assertThat(details.pointsFor(details.match().winner())).isEqualTo(9);
        assertThat(details.pointsFor(details.match().winner().opponent())).isEqualTo(2);
    }

    private SearchMatches.Page search(UUID seasonId, UUID playerId, LocalDate from, LocalDate to,
                                      Set<MatchOutcome> outcomes, int page, int size) {
        return useCase.execute(RankingFixture.user(world.ana), TestLeague.ID,
                new SearchMatches.Criteria(seasonId, playerId, from, to, outcomes, page, size));
    }

    private static List<UUID> ids(SearchMatches.Page page) {
        return page.items().stream().map(d -> d.match().id()).toList();
    }
}
