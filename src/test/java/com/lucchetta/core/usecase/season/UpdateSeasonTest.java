package com.lucchetta.core.usecase.season;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.PastPlayingDays;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.SeasonAuditEntry;
import com.lucchetta.core.domain.SeasonStatus;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.ForbiddenException;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.support.DirectTransactionRunner;
import com.lucchetta.core.support.InMemorySeasons;
import com.lucchetta.core.support.TestLeague;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.ADMIN;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.ADMIN_PLAYER;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.CLOCK;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.NOW;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.REGULAR_USER;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.REGULAR_PLAYER;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.TODAY;
import static com.lucchetta.core.usecase.season.SeasonUseCaseFixture.storedSeason;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UpdateSeasonTest {

    @Mock
    private GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;

    private final InMemorySeasons seasons = new InMemorySeasons();
    private final TestLeague league = new TestLeague();
    private UpdateSeason useCase;

    @BeforeEach
    void setUp() {
        lenient().when(getOrCreateCurrentPlayer.execute(ADMIN)).thenReturn(ADMIN_PLAYER);
        lenient().when(getOrCreateCurrentPlayer.execute(REGULAR_USER)).thenReturn(REGULAR_PLAYER);
        useCase = new UpdateSeason(league.access(getOrCreateCurrentPlayer), seasons, seasons,
                new DirectTransactionRunner(), CLOCK);
    }

    @Test
    void closedSeasonCannotBeChangedOrReopened() {
        Season closed = storedSeason(seasons, "Inverno", TODAY.minusDays(90), TODAY.minusDays(1));

        assertThatThrownBy(() -> execute(ADMIN, closed.id(), endDate(TODAY.plusDays(30))))
                .hasMessage("SEASON_CLOSED");
        assertThatThrownBy(() -> execute(ADMIN, closed.id(),
                new UpdateSeason.Command("Novo nome", null, null, null)))
                .hasMessage("SEASON_CLOSED");
        assertThat(seasons.audit()).isEmpty();
    }

    @Test
    void activeSeasonCanBeExtendedAndChangeIsAudited() {
        Season active = storedSeason(seasons, "Primavera", TODAY.minusDays(10), TODAY.plusDays(20));

        SeasonView view = execute(ADMIN, active.id(), endDate(TODAY.plusDays(50)));

        assertThat(view.season().endDate()).isEqualTo(TODAY.plusDays(50));
        assertThat(view.season().startDate()).isEqualTo(active.startDate());
        assertThat(view.season().updatedAt()).isEqualTo(NOW);
        assertThat(view.status()).isEqualTo(SeasonStatus.ACTIVE);

        SeasonAuditEntry entry = seasons.audit().getFirst();
        assertThat(entry.action()).isEqualTo(SeasonAuditEntry.Action.UPDATED);
        assertThat(entry.performedBy()).isEqualTo(ADMIN_PLAYER.id());
        assertThat(entry.before()).isEqualTo(active);
        assertThat(entry.after()).isEqualTo(view.season());
    }

    @Test
    void activeSeasonCanBeClosedEarlyEndingToday() {
        Season active = storedSeason(seasons, "Primavera", TODAY.minusDays(10), TODAY.plusDays(20));

        SeasonView view = execute(ADMIN, active.id(), endDate(TODAY));

        assertThat(view.season().endDate()).isEqualTo(TODAY);
        assertThat(view.season().statusOn(TODAY.plusDays(1))).isEqualTo(SeasonStatus.CLOSED);
    }

    @Test
    void endDateCannotBeMovedBeforeToday() {
        Season active = storedSeason(seasons, "Primavera", TODAY.minusDays(10), TODAY.plusDays(20));

        assertThatThrownBy(() -> execute(ADMIN, active.id(), endDate(TODAY.minusDays(1))))
                .hasMessage("SEASON_END_IN_PAST");
    }

    @Test
    void activeSeasonStartDateIsLocked() {
        Season active = storedSeason(seasons, "Primavera", TODAY.minusDays(10), TODAY.plusDays(20));

        assertThatThrownBy(() -> execute(ADMIN, active.id(),
                new UpdateSeason.Command(null, TODAY.minusDays(5), null, null)))
                .hasMessage("SEASON_START_LOCKED");
    }

    @Test
    void activeSeasonCanBeRenamed() {
        Season active = storedSeason(seasons, "Primavera", TODAY.minusDays(10), TODAY.plusDays(20));

        SeasonView view = execute(ADMIN, active.id(), new UpdateSeason.Command("Primavera 2026", null, null, null));

        assertThat(view.season().name()).isEqualTo("Primavera 2026");
    }

    @Test
    void plannedSeasonCanChangeAllFields() {
        Season planned = storedSeason(seasons, "Verão", TODAY.plusDays(30), TODAY.plusDays(90));

        SeasonView view = execute(ADMIN, planned.id(),
                new UpdateSeason.Command("Verão 2027", TODAY.plusDays(40), TODAY.plusDays(100), null));

        assertThat(view.season().name()).isEqualTo("Verão 2027");
        assertThat(view.season().startDate()).isEqualTo(TODAY.plusDays(40));
        assertThat(view.season().endDate()).isEqualTo(TODAY.plusDays(100));
        assertThat(view.status()).isEqualTo(SeasonStatus.PLANNED);
    }

    @Test
    void plannedSeasonCanChangePlayingDays() {
        Season planned = storedSeason(seasons, "Verão", TODAY.plusDays(30), TODAY.plusDays(90));

        SeasonView view = execute(ADMIN, planned.id(),
                new UpdateSeason.Command(null, null, null, Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)));

        assertThat(view.season().playingDays()).containsExactly(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(seasons.audit()).singleElement().satisfies(entry -> {
            assertThat(entry.before().playingDays()).hasSize(7);
            assertThat(entry.after()).isEqualTo(view.season());
        });
    }

    @Test
    void activeSeasonPlayingDaysChangeFromTodayOn() {
        Season active = storedSeason(seasons, "Primavera", TODAY.minusDays(10), TODAY.plusDays(20));

        SeasonView view = execute(ADMIN, active.id(),
                new UpdateSeason.Command(null, null, null, Set.of(DayOfWeek.MONDAY)));

        assertThat(view.season().playingDays()).containsExactly(DayOfWeek.MONDAY);
        assertThat(view.season().pastPlayingDays())
                .containsExactly(new PastPlayingDays(TODAY.minusDays(1), active.playingDays()));
        for (int daysAgo = 1; daysAgo <= 10; daysAgo++) {
            assertThat(view.season().isPlayingDay(TODAY.minusDays(daysAgo))).isTrue();
        }
        for (int daysAhead = 0; daysAhead < 7; daysAhead++) {
            LocalDate day = TODAY.plusDays(daysAhead);
            assertThat(view.season().isPlayingDay(day)).isEqualTo(day.getDayOfWeek() == DayOfWeek.MONDAY);
        }
        assertThat(seasons.audit()).singleElement()
                .satisfies(entry -> assertThat(entry.after()).isEqualTo(view.season()));
    }

    @Test
    void cannotExtendIntoAnotherSeason() {
        Season active = storedSeason(seasons, "Primavera", TODAY.minusDays(10), TODAY.plusDays(20));
        storedSeason(seasons, "Verão", TODAY.plusDays(30), TODAY.plusDays(90));

        assertThatThrownBy(() -> execute(ADMIN, active.id(), endDate(TODAY.plusDays(30))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("SEASON_OVERLAP");
    }

    @Test
    void ownPeriodDoesNotCountAsOverlap() {
        Season active = storedSeason(seasons, "Primavera", TODAY.minusDays(10), TODAY.plusDays(20));

        SeasonView view = execute(ADMIN, active.id(), endDate(TODAY.plusDays(15)));

        assertThat(view.season().endDate()).isEqualTo(TODAY.plusDays(15));
    }

    @Test
    void unchangedValuesAreNotAudited() {
        Season active = storedSeason(seasons, "Primavera", TODAY.minusDays(10), TODAY.plusDays(20));

        SeasonView view = execute(ADMIN, active.id(),
                new UpdateSeason.Command(" Primavera ", active.startDate(), active.endDate(), null));

        assertThat(view.season()).isEqualTo(active);
        assertThat(seasons.audit()).isEmpty();
    }

    @Test
    void unknownSeason() {
        assertThatThrownBy(() -> execute(ADMIN, UUID.randomUUID(), endDate(TODAY)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("SEASON_NOT_FOUND");
    }

    @Test
    void regularUserIsForbidden() {
        Season active = storedSeason(seasons, "Primavera", TODAY.minusDays(10), TODAY.plusDays(20));

        assertThatThrownBy(() -> execute(REGULAR_USER, active.id(), endDate(TODAY.plusDays(50))))
                .isInstanceOf(ForbiddenException.class);
        assertThat(seasons.findById(active.id())).contains(active);
    }

    private static UpdateSeason.Command endDate(LocalDate endDate) {
        return new UpdateSeason.Command(null, null, endDate, null);
    }

    private SeasonView execute(AuthenticatedUser user, UUID seasonId, UpdateSeason.Command command) {
        return useCase.execute(user, TestLeague.ID, seasonId, command);
    }
}
