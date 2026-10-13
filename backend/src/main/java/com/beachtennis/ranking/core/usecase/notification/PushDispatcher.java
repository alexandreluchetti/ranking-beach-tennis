package com.beachtennis.ranking.core.usecase.notification;

import com.beachtennis.ranking.core.domain.notification.Notification;
import com.beachtennis.ranking.core.port.DeviceTokenRepository;
import com.beachtennis.ranking.core.port.PushSender;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sends a push copy of newly stored notifications. Best-effort: failures never undo the
 * operation or the in-app notification. The person who performed the action gets no push about
 * it (the in-app record is kept).
 */
public class PushDispatcher {

    private static final System.Logger LOG = System.getLogger(PushDispatcher.class.getName());

    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;

    public PushDispatcher(DeviceTokenRepository deviceTokenRepository, PushSender pushSender) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushSender = pushSender;
    }

    public void dispatch(List<Notification> notifications, UUID actorId) {
        List<Notification> toPush = notifications.stream()
                .filter(n -> !n.recipientId().equals(actorId))
                .toList();
        if (toPush.isEmpty()) {
            return;
        }
        try {
            Map<UUID, List<DeviceTokenRepository.DeviceToken>> devices = deviceTokenRepository
                    .findByPlayers(toPush.stream().map(Notification::recipientId).collect(Collectors.toSet()))
                    .stream()
                    .collect(Collectors.groupingBy(DeviceTokenRepository.DeviceToken::playerId));
            for (Notification notification : toPush) {
                for (DeviceTokenRepository.DeviceToken device : devices.getOrDefault(notification.recipientId(), List.of())) {
                    send(notification, device.token());
                }
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Push dispatch failed; in-app notifications are kept", e);
        }
    }

    private void send(Notification notification, String token) {
        Map<String, String> data = Map.of(
                "notificationId", notification.id().toString(),
                "type", notification.type().name(),
                "matchId", Objects.toString(notification.matchId(), ""));
        PushSender.Result result;
        try {
            result = pushSender.send(token, notification.title(), notification.body(), data);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Push to one device failed", e);
            return;
        }
        if (result == PushSender.Result.INVALID_TOKEN) {
            deviceTokenRepository.deleteInvalid(token);
        }
    }
}
