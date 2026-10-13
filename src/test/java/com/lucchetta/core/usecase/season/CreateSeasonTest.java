package com.lucchetta.core.usecase.season;

import com.lucchetta.core.domain.SeasonAuditEntry;
import com.lucchetta.core.domain.SeasonStatus;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.ForbiddenException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.support.DirectTransactionRunner;
import com.lucchetta.core.support.InMemorySeasons;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.ADMIN;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.ADMIN_PLAYER;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.CLOCK;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.NOW;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.REGULAR_USER;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.TODAY;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.storedSeason;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CreateSeasonTest {

    @Mock
    private GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;

    private final InMemorySeasons seasons = new InMemorySeasons();
    private CreateSeason useCase;

    @BeforeEach
    void setUp() {
        lenient().when(getOrCreateCurrentPlayer.execute(ADMIN)).thenReturn(ADMIN_PLAYER);
        useCase = new CreateSeason(getOrCreateCurrentPlayer, seasons, seasons, new DirectTransactionRunner(), CLOCK);
    }

    @Test
    void adminCreatesPlannedSeasonAndItIsAudited() {
        SeasonView view = useCase.execute(ADMIN, command("Verão 2027", TODAY.plusDays(30), TODAY.plusDays(120)));

        assertThat(view.status()).isEqualTo(SeasonStatus.PLANNED);
        assertThat(view.season().createdBy()).isEqualTo(ADMIN_PLAYER.id());
        assertThat(seasons.findAll()).containsExactly(view.season());

        SeasonAuditEntry entry = seasons.audit().getFirst();
        assertThat(entry.action()).isEqualTo(SeasonAuditEntry.Action.CREATED);
        assertThat(entry.performedBy()).isEqualTo(ADMIN_PLAYER.id());
        assertThat(entry.performedAt()).isEqualTo(NOW);
        assertThat(entry.before()).isNull();
        assertThat(entry.after()).isEqualTo(view.season());
    }

    @Test
    void seasonCoveringTodayIsActiveImmediately() {
        SeasonView view = useCase.execute(ADMIN, command("Primavera", TODAY.minusDays(11), TODAY.plusDays(60)));

        assertThat(view.status()).isEqualTo(SeasonStatus.ACTIVE);
    }

    @Test
    void regularUserIsForbidden() {
        assertThatThrownBy(() -> useCase.execute(REGULAR_USER, command("Primavera", TODAY, TODAY.plusDays(60))))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(getOrCreateCurrentPlayer);
        assertThat(seasons.findAll()).isEmpty();
    }

    @Test
    void rejectsSeasonEndingBeforeToday() {
        assertThatThrownBy(() -> useCase.execute(ADMIN, command("Antiga", TODAY.minusDays(60), TODAY.minusDays(1))))
                .isInstanceOf(RuleViolationException.class)
                .hasMessage("SEASON_END_IN_PAST");
    }

    @Test
    void rejectsInvalidPeriod() {
        assertThatThrownBy(() -> useCase.execute(ADMIN, command("Primavera", TODAY.plusDays(10), TODAY.plusDays(5))))
                .hasMessage("INVALID_SEASON_PERIOD");
    }

    @Test
    void rejectsOverlapWithExistingSeasonIncludingBoundaryDay() {
        storedSeason(seasons, "Primavera", TODAY.minusDays(10), TODAY.plusDays(20));

        assertThatThrownBy(() -> useCase.execute(ADMIN, command("Verão", TODAY.plusDays(20), TODAY.plusDays(90))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("SEASON_OVERLAP");
        assertThat(seasons.findAll()).hasSize(1);
        assertThat(seasons.audit()).isEmpty();
    }

    @Test
    void allowsSeasonStartingTheDayAfterAnotherEnds() {
        storedSeason(seasons, "Primavera", TODAY.minusDays(10), TODAY.plusDays(20));

        useCase.execute(ADMIN, command("Verão", TODAY.plusDays(21), TODAY.plusDays(90)));

        assertThat(seasons.findAll()).hasSize(2);
    }

    private static CreateSeason.Command command(String name, LocalDate start, LocalDate end) {
        return new CreateSeason.Command(name, start, end);
    }
}
