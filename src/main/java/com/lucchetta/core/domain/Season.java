package com.lucchetta.core.domain;

import com.lucchetta.core.exception.RuleViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A ranking season. Both dates are inclusive. The status is derived from the dates, so a season
 * closes automatically once its end date has passed (decision D-006).
 */
public record Season(
        UUID id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static final int NAME_MIN_LENGTH = 2;
    public static final int NAME_MAX_LENGTH = 60;

    public Season {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        name = normalizeName(name);
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new RuleViolationException("INVALID_SEASON_PERIOD");
        }
    }

    public SeasonStatus statusOn(LocalDate today) {
        if (today.isBefore(startDate)) {
            return SeasonStatus.PLANNED;
        }
        return today.isAfter(endDate) ? SeasonStatus.CLOSED : SeasonStatus.ACTIVE;
    }

    public boolean overlaps(LocalDate otherStart, LocalDate otherEnd) {
        return !startDate.isAfter(otherEnd) && !otherStart.isAfter(endDate);
    }

    public Season withChanges(String newName, LocalDate newStart, LocalDate newEnd, Instant now) {
        return new Season(id, newName, newStart, newEnd, createdBy, createdAt, now);
    }

    private static String normalizeName(String value) {
        String normalized = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (normalized.length() < NAME_MIN_LENGTH || normalized.length() > NAME_MAX_LENGTH) {
            throw new RuleViolationException("INVALID_SEASON_NAME");
        }
        return normalized;
    }
}
