package com.lucchetta.core.domain;

import com.lucchetta.core.exception.RuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
        return new Season(UUID.randomUUID(), name, start, end, UUID.randomUUID(), now, now);
    }
}
