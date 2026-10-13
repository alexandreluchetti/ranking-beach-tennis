package com.beachtennis.ranking.core.port;

import com.beachtennis.ranking.core.domain.notification.Notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository {

    /**
     * Stores the notifications whose dedup key is not taken yet.
     *
     * @return only the notifications actually stored now
     */
    List<Notification> insertNew(List<Notification> notifications);

    /** Most recent first. */
    List<Notification> findByRecipient(UUID recipientId, int offset, int limit);

    int countUnread(UUID recipientId);

    /** @return false when the notification does not exist or belongs to someone else */
    boolean markRead(UUID notificationId, UUID recipientId, Instant readAt);

    void markAllRead(UUID recipientId, Instant readAt);
}
