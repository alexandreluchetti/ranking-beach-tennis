package com.lucchetta.core.usecase.notification;

import com.lucchetta.core.domain.notification.DevicePlatform;
import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.domain.notification.NotificationType;
import com.lucchetta.core.port.PushSender;
import com.lucchetta.core.support.InMemoryNotifications;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PushDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    private final InMemoryNotifications store = new InMemoryNotifications();
    private final UUID ana = UUID.randomUUID();
    private final UUID beto = UUID.randomUUID();

    @Test
    void pushesToEveryDeviceOfEachRecipientExceptTheActor() {
        store.save("ana-phone", ana, DevicePlatform.ANDROID, NOW);
        store.save("ana-tablet", ana, DevicePlatform.IOS, NOW);
        store.save("beto-phone", beto, DevicePlatform.ANDROID, NOW);

        new PushDispatcher(store, store).dispatch(List.of(notification(ana), notification(beto)), beto);

        assertThat(store.pushedTokens()).containsExactlyInAnyOrder("ana-phone", "ana-tablet");
    }

    @Test
    void invalidTokensAreForgotten() {
        store.save("old-phone", ana, DevicePlatform.ANDROID, NOW);
        store.pushResult(PushSender.Result.INVALID_TOKEN);

        new PushDispatcher(store, store).dispatch(List.of(notification(ana)), beto);

        assertThat(store.hasDevice("old-phone")).isFalse();
    }

    @Test
    void failuresNeverPropagate() {
        store.save("ana-phone", ana, DevicePlatform.ANDROID, NOW);
        PushSender broken = (token, title, body, data) -> {
            throw new IllegalStateException("FCM unavailable");
        };

        assertThatCode(() -> new PushDispatcher(store, broken).dispatch(List.of(notification(ana)), beto))
                .doesNotThrowAnyException();
        assertThat(store.hasDevice("ana-phone")).isTrue();
    }

    @Test
    void sendsNotificationIdTypeAndMatchAsData() {
        store.save("ana-phone", ana, DevicePlatform.ANDROID, NOW);
        Notification notification = notification(ana);
        Map<String, String>[] captured = new Map[1];
        PushSender capturing = (token, title, body, data) -> {
            captured[0] = data;
            return PushSender.Result.SENT;
        };

        new PushDispatcher(store, capturing).dispatch(List.of(notification), beto);

        assertThat(captured[0]).containsEntry("notificationId", notification.id().toString())
                .containsEntry("type", "MATCH_REGISTERED")
                .containsEntry("matchId", notification.matchId().toString());
    }

    private static Notification notification(UUID recipient) {
        UUID id = UUID.randomUUID();
        return new Notification(id, recipient, NotificationType.MATCH_REGISTERED, UUID.randomUUID(), "Partida registrada",
                "corpo", NOW, null, "key:" + id);
    }
}
