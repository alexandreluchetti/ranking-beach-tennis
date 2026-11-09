package com.lucchetta.core.usecase.notification;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.notification.DevicePlatform;
import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.DeviceTokenRepository;
import com.lucchetta.core.port.NotificationRepository;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The caller's own notifications and push devices. Nobody can read or change another
 * player's notifications.
 */
public class NotificationInbox {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_DELETE_BATCH = 100;
    static final int MAX_TOKEN_LENGTH = 512;

    public record Page(List<Notification> items, int unreadCount, int page, int size, boolean hasNext) {
    }

    private final GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final Clock clock;

    public NotificationInbox(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                             NotificationRepository notificationRepository,
                             DeviceTokenRepository deviceTokenRepository,
                             Clock clock) {
        this.getOrCreateCurrentPlayer = getOrCreateCurrentPlayer;
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.clock = clock;
    }

    public Page list(AuthenticatedUser user, int page, int size) {
        UUID me = me(user);
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        List<Notification> items = notificationRepository.findByRecipient(me, safePage * safeSize, safeSize + 1);
        boolean hasNext = items.size() > safeSize;
        return new Page(hasNext ? items.subList(0, safeSize) : items, notificationRepository.countUnread(me),
                safePage, safeSize, hasNext);
    }

    public void markRead(AuthenticatedUser user, UUID notificationId) {
        if (!notificationRepository.markRead(notificationId, me(user), clock.instant())) {
            throw new NotFoundException("NOTIFICATION_NOT_FOUND");
        }
    }

    public void markAllRead(AuthenticatedUser user) {
        notificationRepository.markAllRead(me(user), clock.instant());
    }

    /** Deletes the notification for good (D-032); push already delivered to a device is not affected. */
    public void delete(AuthenticatedUser user, UUID notificationId) {
        if (!notificationRepository.delete(notificationId, me(user))) {
            throw new NotFoundException("NOTIFICATION_NOT_FOUND");
        }
    }

    /**
     * Deletes the caller's notifications among the given ids; ids that do not exist or belong to
     * someone else are ignored, so a resend succeeds and nothing is revealed about them (D-032).
     */
    public void delete(AuthenticatedUser user, List<UUID> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty() || notificationIds.size() > MAX_DELETE_BATCH
                || notificationIds.stream().anyMatch(Objects::isNull)) {
            throw new RuleViolationException("NOTIFICATION_IDS_INVALID");
        }
        notificationRepository.deleteAll(me(user), Set.copyOf(notificationIds));
    }

    public void deleteAll(AuthenticatedUser user) {
        notificationRepository.deleteAll(me(user));
    }

    public void registerDevice(AuthenticatedUser user, String token, DevicePlatform platform) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH || platform == null) {
            throw new RuleViolationException("INVALID_DEVICE");
        }
        deviceTokenRepository.save(token.strip(), me(user), platform, clock.instant());
    }

    public void unregisterDevice(AuthenticatedUser user, String token) {
        deviceTokenRepository.delete(token, me(user));
    }

    private UUID me(AuthenticatedUser user) {
        return getOrCreateCurrentPlayer.execute(user).id();
    }
}
