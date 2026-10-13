package com.lucchetta.core.usecase.season;

import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.SeasonStatus;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.support.InMemorySeasons;
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
    private final QuerySeasons useCase = new QuerySeasons(seasons, CLOCK);

    @Test
    void listsMostRecentFirstWithDerivedStatus() {
        storedSeason(seasons, "Inverno", TODAY.minusDays(120), TODAY.minusDays(31));
        storedSeason(seasons, "Primavera", TODAY.minusDays(30), TODAY);
        storedSeason(seasons, "Verão", TODAY.plusDays(1), TODAY.plusDays(90));

        assertThat(useCase.list())
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

        assertThat(useCase.current()).hasValueSatisfying(view -> {
            assertThat(view.season()).isEqualTo(active);
            assertThat(view.status()).isEqualTo(SeasonStatus.ACTIVE);
        });
    }

    @Test
    void noCurrentSeasonBetweenSeasons() {
        storedSeason(seasons, "Inverno", TODAY.minusDays(120), TODAY.minusDays(1));
        storedSeason(seasons, "Verão", TODAY.plusDays(1), TODAY.plusDays(90));

        assertThat(useCase.current()).isEmpty();
    }

    @Test
    void unknownSeasonById() {
        assertThatThrownBy(() -> useCase.byId(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
