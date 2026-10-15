package com.lucchetta.core.support;

import com.lucchetta.core.domain.notification.DevicePlatform;
import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.port.DeviceTokenRepository;
import com.lucchetta.core.port.NotificationRepository;
import com.lucchetta.core.port.PushSender;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** In-memory notifications, device tokens and a recording push sender. */
public class InMemoryNotifications implements NotificationRepository, DeviceTokenRepository, PushSender {

    private final Map<String, Notification> byDedupKey = new LinkedHashMap<>();
    private final Map<String, DeviceToken> devices = new LinkedHashMap<>();
    private final List<String> pushedTokens = new ArrayList<>();
    private Result pushResult = Result.SENT;

    public List<Notification> all() {
        return List.copyOf(byDedupKey.values());
    }

    public List<Notification> of(UUID recipientId) {
        return byDedupKey.values().stream().filter(n -> n.recipientId().equals(recipientId)).toList();
    }

    public List<String> pushedTokens() {
        return pushedTokens;
    }

    public void pushResult(Result result) {
        this.pushResult = result;
    }

    @Override
    public List<Notification> insertNew(List<Notification> notifications) {
        return notifications.stream().filter(n -> byDedupKey.putIfAbsent(n.dedupKey(), n) == null).toList();
    }

    @Override
    public List<Notification> findByRecipient(UUID recipientId, int offset, int limit) {
        return of(recipientId).stream()
                .sorted(Comparator.comparing(Notification::createdAt).reversed())
                .skip(offset).limit(limit).toList();
    }

    @Override
    public int countUnread(UUID recipientId) {
        return (int) of(recipientId).stream().filter(n -> !n.read()).count();
    }

    @Override
    public boolean markRead(UUID notificationId, UUID recipientId, Instant readAt) {
        for (Map.Entry<String, Notification> e : byDedupKey.entrySet()) {
            Notification n = e.getValue();
            if (n.id().equals(notificationId) && n.recipientId().equals(recipientId)) {
                e.setValue(n.read() ? n : n.markedRead(readAt));
                return true;
            }
        }
        return false;
    }

    @Override
    public void markAllRead(UUID recipientId, Instant readAt) {
        of(recipientId).forEach(n -> markRead(n.id(), recipientId, readAt));
    }

    @Override
    public void save(String token, UUID playerId, DevicePlatform platform, Instant now) {
        devices.put(token, new DeviceToken(token, playerId, platform));
    }

    @Override
    public void delete(String token, UUID playerId) {
        devices.computeIfPresent(token, (t, d) -> d.playerId().equals(playerId) ? null : d);
    }

    @Override
    public void deleteInvalid(String token) {
        devices.remove(token);
    }

    @Override
    public List<DeviceToken> findByPlayers(Collection<UUID> playerIds) {
        return devices.values().stream().filter(d -> playerIds.contains(d.playerId())).toList();
    }

    public boolean hasDevice(String token) {
        return devices.containsKey(token);
    }

    @Override
    public Result send(String deviceToken, String title, String body, Map<String, String> data) {
        pushedTokens.add(deviceToken);
        return pushResult;
    }
}
