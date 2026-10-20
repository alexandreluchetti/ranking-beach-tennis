package com.lucchetta.core.domain;

import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.support.TestLeague;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeasonTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void statusUsesInclusiveDates() {
        Season season = season("Primavera", START, END);

        assertThat(season.statusOn(START.minusDays(1))).isEqualTo(SeasonStatus.PLANNED);
        assertThat(season.statusOn(START)).isEqualTo(SeasonStatus.ACTIVE);
        assertThat(season.statusOn(END)).isEqualTo(SeasonStatus.ACTIVE);
        assertThat(season.statusOn(END.plusDays(1))).isEqualTo(SeasonStatus.CLOSED);
    }

    @Test
    void singleDaySeasonIsValid() {
        assertThat(season("Torneio", START, START).statusOn(START)).isEqualTo(SeasonStatus.ACTIVE);
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> season("Primavera", END, START))
                .isInstanceOf(RuleViolationException.class)
                .hasMessage("INVALID_SEASON_PERIOD");
    }

    @Test
    void rejectsMissingDates() {
        assertThatThrownBy(() -> season("Primavera", null, END)).hasMessage("INVALID_SEASON_PERIOD");
        assertThatThrownBy(() -> season("Primavera", START, null)).hasMessage("INVALID_SEASON_PERIOD");
    }

    @Test
    void validatesAndNormalizesName() {
        assertThat(season("  Temporada   2026 ", START, END).name()).isEqualTo("Temporada 2026");
        assertThatThrownBy(() -> season("X", START, END)).hasMessage("INVALID_SEASON_NAME");
        assertThatThrownBy(() -> season(null, START, END)).hasMessage("INVALID_SEASON_NAME");
        assertThatThrownBy(() -> season("x".repeat(61), START, END)).hasMessage("INVALID_SEASON_NAME");
    }

    @Test
    void playingDaysAreRequiredAndDefaultToEveryDay() {
        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        UUID id = UUID.randomUUID();

        assertThat(season("Primavera", START, END).playingDays()).isEqualTo(EnumSet.allOf(DayOfWeek.class));
        assertThatThrownBy(() -> new Season(id, TestLeague.ID, "Primavera", START, END, Set.of(), id, now, now))
                .isInstanceOf(RuleViolationException.class)
                .hasMessage("INVALID_SEASON_PLAYING_DAYS");
        assertThatThrownBy(() -> new Season(id, TestLeague.ID, "Primavera", START, END, null, id, now, now))
                .hasMessage("INVALID_SEASON_PLAYING_DAYS");
    }

    @Test
    void playingDaysAreKeptInWeekOrderAndChecked() {
        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        Season season = new Season(UUID.randomUUID(), TestLeague.ID, "Primavera", START, END,
                Set.of(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY), UUID.randomUUID(), now, now);

        assertThat(List.copyOf(season.playingDays())).containsExactly(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY);
        assertThat(season.isPlayingDay(LocalDate.of(2026, 10, 6))).isTrue();  // Tuesday
        assertThat(season.isPlayingDay(LocalDate.of(2026, 10, 7))).isFalse(); // Wednesday
    }

    @Test
    void playingDaysChangedDuringTheSeasonApplyFromThatDayOn() {
        Set<DayOfWeek> everyDay = EnumSet.allOf(DayOfWeek.class);
        Season season = season("Primavera", START, END); // starts on Thursday, Oct 1
        LocalDate oct10 = LocalDate.of(2026, 10, 10);

        Season tuesdays = season.withPlayingDays(Set.of(DayOfWeek.TUESDAY), oct10, Instant.now());
        assertThat(tuesdays.pastPlayingDays()).containsExactly(new PastPlayingDays(oct10.minusDays(1), everyDay));
        assertThat(tuesdays.isPlayingDay(LocalDate.of(2026, 10, 7))).isTrue();   // Wednesday, before the change
        assertThat(tuesdays.isPlayingDay(LocalDate.of(2026, 10, 14))).isFalse(); // Wednesday, after it
        assertThat(tuesdays.isPlayingDay(LocalDate.of(2026, 10, 13))).isTrue();  // Tuesday

        Season thenSaturdays = tuesdays.withPlayingDays(Set.of(DayOfWeek.SATURDAY), LocalDate.of(2026, 10, 20),
                Instant.now());
        assertThat(thenSaturdays.pastPlayingDays()).containsExactly(
                new PastPlayingDays(oct10.minusDays(1), everyDay),
                new PastPlayingDays(LocalDate.of(2026, 10, 19), Set.of(DayOfWeek.TUESDAY)));
        assertThat(thenSaturdays.isPlayingDay(LocalDate.of(2026, 10, 13))).isTrue();  // Tuesday, middle period
        assertThat(thenSaturdays.isPlayingDay(LocalDate.of(2026, 10, 27))).isFalse(); // Tuesday, after
    }

    @Test
    void secondChangeOnTheSameDayReplacesTheFirst() {
        Season season = season("Primavera", START, END);
        LocalDate oct10 = LocalDate.of(2026, 10, 10);

        Season changedTwice = season.withPlayingDays(Set.of(DayOfWeek.TUESDAY), oct10, Instant.now())
                .withPlayingDays(Set.of(DayOfWeek.FRIDAY), oct10, Instant.now());
        Season undone = season.withPlayingDays(Set.of(DayOfWeek.TUESDAY), oct10, Instant.now())
                .withPlayingDays(EnumSet.allOf(DayOfWeek.class), oct10, Instant.now());

        assertThat(changedTwice.playingDays()).containsExactly(DayOfWeek.FRIDAY);
        assertThat(changedTwice.pastPlayingDays()).hasSize(1);
        assertThat(undone.pastPlayingDays()).isEmpty();
    }

    @Test
    void changeTakingEffectOnTheStartDateReplacesTheDays() {
        Season season = season("Primavera", START, END);

        Season changed = season.withPlayingDays(Set.of(DayOfWeek.SUNDAY), START, Instant.now());

        assertThat(changed.pastPlayingDays()).isEmpty();
        assertThat(changed.isPlayingDay(START)).isFalse();
    }

    @Test
    void overlapIncludesSharedBoundaryDay() {
        Season season = season("Primavera", START, END);

        assertThat(season.overlaps(END, END.plusDays(10))).isTrue();
        assertThat(season.overlaps(START.minusDays(10), START)).isTrue();
        assertThat(season.overlaps(END.plusDays(1), END.plusDays(10))).isFalse();
        assertThat(season.overlaps(START.minusDays(10), START.minusDays(1))).isFalse();
    }

    @Test
    void businessDateIsBrasiliaTime() {
        // 02:00 UTC on Oct 12 is still 23:00 on Oct 11 in Brasília.
        Clock clock = Clock.fixed(Instant.parse("2026-10-12T02:00:00Z"), ZoneOffset.UTC);

        assertThat(BusinessCalendar.today(clock)).isEqualTo(LocalDate.of(2026, 10, 11));
    }

    private static Season season(String name, LocalDate start, LocalDate end) {
        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        return new Season(UUID.randomUUID(), TestLeague.ID, name, start, end, UUID.randomUUID(), now, now);
    }
}
