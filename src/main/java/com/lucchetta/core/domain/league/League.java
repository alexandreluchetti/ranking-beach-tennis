package com.lucchetta.core.domain.league;

import com.lucchetta.core.exception.RuleViolationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A league: a space whose members compete in its own ranking (decision D-017). Leagues are never
 * deleted; an archived league is read-only and hidden from search.
 *
 * @param scoreEntry     who records match scores (decision D-029)
 * @param sharedMatches  whether matches of other leagues whose four players are all members of
 *                       this one count here too (decision D-031)
 * @param createdBy      the player who created it; null only for the migrated league of a database
 *                       without users
 * @param inviteCode     current invite code, or null if none was generated
 */
public record League(
        UUID id,
        String name,
        String description,
        LeagueVisibility visibility,
        ScoreEntry scoreEntry,
        boolean sharedMatches,
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
        if (scoreEntry == null) {
            throw new RuleViolationException("INVALID_LEAGUE_SCORE_ENTRY");
        }
        if ((archivedAt == null) != (archivedBy == null)) {
            throw new IllegalArgumentException("archivedAt and archivedBy go together");
        }
    }

    /** A league that receives shared matches, the setting of every league created before D-031. */
    public League(UUID id, String name, String description, LeagueVisibility visibility, ScoreEntry scoreEntry,
                  String inviteCode, UUID createdBy, Instant createdAt, Instant updatedAt, Instant archivedAt,
                  UUID archivedBy, int version) {
        this(id, name, description, visibility, scoreEntry, true, inviteCode, createdBy, createdAt, updatedAt,
                archivedAt, archivedBy, version);
    }

    /** A league where any member records scores, the setting of every league created before D-029. */
    public League(UUID id, String name, String description, LeagueVisibility visibility, String inviteCode,
                  UUID createdBy, Instant createdAt, Instant updatedAt, Instant archivedAt, UUID archivedBy,
                  int version) {
        this(id, name, description, visibility, ScoreEntry.MEMBERS, inviteCode, createdBy, createdAt, updatedAt,
                archivedAt, archivedBy, version);
    }

    public static League create(String name, String description, LeagueVisibility visibility, ScoreEntry scoreEntry,
                                boolean sharedMatches, UUID createdBy, Instant now) {
        return new League(UUID.randomUUID(), name, description, visibility, scoreEntry, sharedMatches, null, createdBy,
                now, now, null, null, 0);
    }

    public boolean archived() {
        return archivedAt != null;
    }

    /** Whether this member may record match scores (D-029); corrections stay with administrators. */
    public boolean allowsScoreEntryBy(boolean administrator) {
        return administrator || scoreEntry == ScoreEntry.MEMBERS;
    }

    public League withDetails(String newName, String newDescription, LeagueVisibility newVisibility,
                              ScoreEntry newScoreEntry, Instant now) {
        return new League(id, newName, newDescription, newVisibility, newScoreEntry, sharedMatches, inviteCode,
                createdBy, createdAt, now, archivedAt, archivedBy, version);
    }

    public League withSharedMatches(boolean newSharedMatches, Instant now) {
        return newSharedMatches == sharedMatches ? this : new League(id, name, description, visibility, scoreEntry,
                newSharedMatches, inviteCode, createdBy, createdAt, now, archivedAt, archivedBy, version);
    }

    /** A new invite code invalidates the previous one (decision D-019). */
    public League withInviteCode(String code, Instant now) {
        return new League(id, name, description, visibility, scoreEntry, sharedMatches, code, createdBy, createdAt,
                now, archivedAt, archivedBy, version);
    }

    public League archive(UUID by, Instant now) {
        return new League(id, name, description, visibility, scoreEntry, sharedMatches, inviteCode, createdBy,
                createdAt, now, now, by, version);
    }

    public League unarchive(Instant now) {
        return new League(id, name, description, visibility, scoreEntry, sharedMatches, inviteCode, createdBy,
                createdAt, now, null, null, version);
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
