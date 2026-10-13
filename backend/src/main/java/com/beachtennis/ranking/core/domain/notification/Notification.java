package com.beachtennis.ranking.core.domain.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * In-app notification. It is the record of truth: push messages are only a best-effort copy.
 *
 * @param dedupKey one notification per event and recipient; retries and resends never create a
 *                 second one
 * @param readAt   null while unread
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
        String dedupKey) {

    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(dedupKey, "dedupKey");
    }

    public boolean read() {
        return readAt != null;
    }
}
