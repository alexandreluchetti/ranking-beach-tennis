package com.lucchetta.core.domain.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * In-app notification. It is the record of truth: push messages are only a best-effort copy.
 *
 * @param matchId       the match of a match notification, otherwise null
 * @param dedupKey      one notification per event and recipient; retries and resends never create
 *                      a second one
 * @param readAt        null while unread
 * @param leagueId      the league of a league notification, otherwise null
 * @param joinRequestId the join request a league administrator may answer, otherwise null
 * @param invitationId  the invitation the recipient may answer, otherwise null
 */
public record Notification(
        UUID id,
        UUID recipientId,
        NotificationType type,
        UUID matchId,
        String title,
        String body,
        Instant createdAt,
        Instant readAt,
        String dedupKey,
        UUID leagueId,
        UUID joinRequestId,
        UUID invitationId) {

    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(dedupKey, "dedupKey");
    }

    /** A match notification. */
    public Notification(UUID id, UUID recipientId, NotificationType type, UUID matchId, String title, String body,
                        Instant createdAt, Instant readAt, String dedupKey) {
        this(id, recipientId, type, matchId, title, body, createdAt, readAt, dedupKey, null, null, null);
    }

    public boolean read() {
        return readAt != null;
    }

    public Notification markedRead(Instant at) {
        return new Notification(id, recipientId, type, matchId, title, body, createdAt, at, dedupKey, leagueId,
                joinRequestId, invitationId);
    }
}
