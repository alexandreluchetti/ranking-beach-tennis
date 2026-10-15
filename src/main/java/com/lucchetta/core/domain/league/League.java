package com.lucchetta.core.domain.league;

import com.lucchetta.core.exception.RuleViolationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A league: a space whose members compete in its own ranking (decision D-017). Leagues are never
 * deleted; an archived league is read-only and hidden from search.
 *
 * @param createdBy  the player who created it; null only for the migrated league of a database
 *                   without users
 * @param inviteCode current invite code, or null if none was generated
 */
public record League(
        UUID id,
        String name,
        String description,
        LeagueVisibility visibility,
        String inviteCode,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt,
        UUID archivedBy,
        int version) {

    public static final int NAME_MIN_LENGTH = 3;
    public static final int NAME_MAX_LENGTH = 60;
    public static final int DESCRIPTION_MAX_LENGTH = 500;
    /** Members per league, administrators included. */
    public static final int MAX_MEMBERS = 50;
    /** Non-archived leagues a user may have created; the system administrator has no limit. */
    public static final int MAX_ACTIVE_CREATED_PER_USER = 5;

    public League {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        name = normalizeName(name);
        description = normalizeDescription(description);
        if (visibility == null) {
            throw new RuleViolationException("INVALID_LEAGUE_VISIBILITY");
        }
        if ((archivedAt == null) != (archivedBy == null)) {
            throw new IllegalArgumentException("archivedAt and archivedBy go together");
        }
    }

    public static League create(String name, String description, LeagueVisibility visibility, UUID createdBy,
                                Instant now) {
        return new League(UUID.randomUUID(), name, description, visibility, null, createdBy, now, now, null, null, 0);
    }

    public boolean archived() {
        return archivedAt != null;
    }

    public League withDetails(String newName, String newDescription, LeagueVisibility newVisibility, Instant now) {
        return new League(id, newName, newDescription, newVisibility, inviteCode, createdBy, createdAt, now,
                archivedAt, archivedBy, version);
    }

    public League archive(UUID by, Instant now) {
        return new League(id, name, description, visibility, inviteCode, createdBy, createdAt, now, now, by, version);
    }

    public League unarchive(Instant now) {
        return new League(id, name, description, visibility, inviteCode, createdBy, createdAt, now, null, null,
                version);
    }

    private static String normalizeName(String value) {
        String normalized = value == null ? "" : value.strip().replaceAll("\s+", " ");
        if (normalized.length() < NAME_MIN_LENGTH || normalized.length() > NAME_MAX_LENGTH) {
            throw new RuleViolationException("INVALID_LEAGUE_NAME");
        }
        return normalized;
    }

    private static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > DESCRIPTION_MAX_LENGTH) {
            throw new RuleViolationException("INVALID_LEAGUE_DESCRIPTION");
        }
        return normalized;
    }
}
