package com.lucchetta.core.domain;

import com.lucchetta.core.exception.RuleViolationException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A ranking season of a league; seasons of one league never overlap (D-008). Both dates are
 * inclusive. The status is derived from the dates, so a season closes automatically once its end
 * date has passed (decision D-006). Only matches played on one of the season's playing days
 * (Brasília) count for its ranking (D-025).
 *
 * @param playingDays     the current playing days
 * @param pastPlayingDays earlier playing days, oldest first, kept when the days changed while the
 *                        season was active, so matches played before the change keep counting (or
 *                        not) as they did
 */
public record Season(
        UUID id,
        UUID leagueId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        Set<DayOfWeek> playingDays,
        List<PastPlayingDays> pastPlayingDays,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static final int NAME_MIN_LENGTH = 2;
    public static final int NAME_MAX_LENGTH = 60;

    public Season {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(leagueId, "leagueId");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        name = normalizeName(name);
        if (playingDays == null || playingDays.isEmpty()) {
            throw new RuleViolationException("INVALID_SEASON_PLAYING_DAYS");
        }
        playingDays = Collections.unmodifiableSet(EnumSet.copyOf(playingDays));
        pastPlayingDays = List.copyOf(Objects.requireNonNullElse(pastPlayingDays, List.of()));
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new RuleViolationException("INVALID_SEASON_PERIOD");
        }
        LocalDate previous = startDate.minusDays(1);
        for (PastPlayingDays past : pastPlayingDays) {
            if (!past.lastDay().isAfter(previous) || past.lastDay().isAfter(endDate)) {
                throw new IllegalArgumentException("Past playing days out of order or outside the season");
            }
            previous = past.lastDay();
        }
    }

    public Season(UUID id, UUID leagueId, String name, LocalDate startDate, LocalDate endDate,
                  Set<DayOfWeek> playingDays, UUID createdBy, Instant createdAt, Instant updatedAt) {
        this(id, leagueId, name, startDate, endDate, playingDays, List.of(), createdBy, createdAt, updatedAt);
    }

    /** Seasons created before D-025 (and requests that omit the days) play every day of the week. */
    public Season(UUID id, UUID leagueId, String name, LocalDate startDate, LocalDate endDate, UUID createdBy,
                  Instant createdAt, Instant updatedAt) {
        this(id, leagueId, name, startDate, endDate, EnumSet.allOf(DayOfWeek.class), createdBy, createdAt, updatedAt);
    }

    public SeasonStatus statusOn(LocalDate today) {
        if (today.isBefore(startDate)) {
            return SeasonStatus.PLANNED;
        }
        return today.isAfter(endDate) ? SeasonStatus.CLOSED : SeasonStatus.ACTIVE;
    }

    /**
     * Whether a match played on this business date counts for the season's ranking (D-025): the
     * playing days in effect on that date decide, so a change never affects earlier days.
     */
    public boolean isPlayingDay(LocalDate day) {
        return pastPlayingDays.stream()
                .filter(past -> !day.isAfter(past.lastDay()))
                .findFirst()
                .map(PastPlayingDays::playingDays)
                .orElse(playingDays)
                .contains(day.getDayOfWeek());
    }

    public boolean overlaps(LocalDate otherStart, LocalDate otherEnd) {
        return !startDate.isAfter(otherEnd) && !otherStart.isAfter(endDate);
    }

    public Season withChanges(String newName, LocalDate newStart, LocalDate newEnd, Instant now) {
        return new Season(id, leagueId, newName, newStart, newEnd, playingDays, pastPlayingDays, createdBy, createdAt,
                now);
    }

    /**
     * New playing days, in effect from {@code effectiveFrom} on (D-025). Days before it keep the
     * playing days they had; when the change takes effect on or before the start date (planned
     * season, or first day of the season), the new days simply replace the old ones. A second
     * change taking effect on the same day replaces the first one.
     */
    public Season withPlayingDays(Set<DayOfWeek> newPlayingDays, LocalDate effectiveFrom, Instant now) {
        if (playingDays.equals(newPlayingDays)) {
            return this;
        }
        LocalDate lastDay = effectiveFrom.minusDays(1);
        List<PastPlayingDays> past = new ArrayList<>(pastPlayingDays);
        past.removeIf(p -> !p.lastDay().isBefore(effectiveFrom));
        boolean changedEarlierTheSameDay = !past.isEmpty() && past.getLast().lastDay().equals(lastDay);
        if (changedEarlierTheSameDay && past.getLast().playingDays().equals(newPlayingDays)) {
            past.removeLast(); // today's earlier change undone
        } else if (!lastDay.isBefore(startDate) && !changedEarlierTheSameDay) {
            past.add(new PastPlayingDays(lastDay, playingDays));
        }
        return new Season(id, leagueId, name, startDate, endDate, newPlayingDays, past, createdBy, createdAt, now);
    }

    private static String normalizeName(String value) {
        String normalized = value == null ? "" : value.strip().replaceAll("\s+", " ");
        if (normalized.length() < NAME_MIN_LENGTH || normalized.length() > NAME_MAX_LENGTH) {
            throw new RuleViolationException("INVALID_SEASON_NAME");
        }
        return normalized;
    }
}
