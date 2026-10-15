package com.lucchetta.core.usecase.season;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.SeasonStatus;
import com.lucchetta.core.exception.ForbiddenException;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.support.InMemoryPlayers;
import com.lucchetta.core.support.InMemorySeasons;
import com.lucchetta.core.support.TestLeague;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.CLOCK;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.TODAY;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.storedSeason;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class QuerySeasonsTest {

    private final InMemorySeasons seasons = new InMemorySeasons();
    private final InMemoryPlayers players = new InMemoryPlayers();
    private final TestLeague league = new TestLeague();
    private final QuerySeasons useCase = new QuerySeasons(league.access(new GetOrCreateCurrentPlayer(players, CLOCK)),
            seasons, CLOCK);
    private final Player ana = players.add("Ana");
    private final AuthenticatedUser member = new AuthenticatedUser(ana.identity(), null, "Ana", false);

    {
        league.members(ana);
    }

    @Test
    void listsMostRecentFirstWithDerivedStatus() {
        storedSeason(seasons, "Inverno", TODAY.minusDays(120), TODAY.minusDays(31));
        storedSeason(seasons, "Primavera", TODAY.minusDays(30), TODAY);
        storedSeason(seasons, "Verão", TODAY.plusDays(1), TODAY.plusDays(90));

        assertThat(useCase.list(member, TestLeague.ID))
                .extracting(view -> view.season().name(), SeasonView::status)
                .containsExactly(
                        tuple("Verão", SeasonStatus.PLANNED),
                        tuple("Primavera", SeasonStatus.ACTIVE),
                        tuple("Inverno", SeasonStatus.CLOSED));
    }

    @Test
    void currentIsTheSeasonCoveringToday() {
        storedSeason(seasons, "Inverno", TODAY.minusDays(120), TODAY.minusDays(1));
        Season active = storedSeason(seasons, "Primavera", TODAY, TODAY.plusDays(30));

        assertThat(useCase.current(member, TestLeague.ID)).hasValueSatisfying(view -> {
            assertThat(view.season()).isEqualTo(active);
            assertThat(view.status()).isEqualTo(SeasonStatus.ACTIVE);
        });
    }

    @Test
    void noCurrentSeasonBetweenSeasons() {
        storedSeason(seasons, "Inverno", TODAY.minusDays(120), TODAY.minusDays(1));
        storedSeason(seasons, "Verão", TODAY.plusDays(1), TODAY.plusDays(90));

        assertThat(useCase.current(member, TestLeague.ID)).isEmpty();
    }

    @Test
    void unknownSeasonById() {
        assertThatThrownBy(() -> useCase.byId(member, TestLeague.ID, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void seasonsOfAnotherLeagueAreNotFoundAndOutsidersSeeNothing() {
        Season active = storedSeason(seasons, "Primavera", TODAY, TODAY.plusDays(30));
        Player outsider = players.add("Fora");

        assertThatThrownBy(() -> useCase.byId(member, UUID.randomUUID(), active.id()))
                .hasMessage("LEAGUE_NOT_FOUND");
        assertThatThrownBy(() -> useCase.list(new AuthenticatedUser(outsider.identity(), null, "Fora", false),
                TestLeague.ID)).isInstanceOf(ForbiddenException.class);
    }
}
