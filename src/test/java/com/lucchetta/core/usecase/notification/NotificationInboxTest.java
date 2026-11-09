package com.lucchetta.core.usecase.notification;

import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.domain.notification.NotificationType;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.support.InMemoryNotifications;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;
import com.lucchetta.core.usecase.ranking.RankingFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Decision D-032: users delete their own notifications for good. */
class NotificationInboxTest {

    private final RankingFixture world = new RankingFixture();
    private final InMemoryNotifications notifications = new InMemoryNotifications();
    private final NotificationInbox inbox = new NotificationInbox(
            new GetOrCreateCurrentPlayer(world.players, RankingFixture.CLOCK), notifications, notifications,
            RankingFixture.CLOCK);

    private final Notification anaFirst = notify(world.ana.id());
    private final Notification anaSecond = notify(world.ana.id());
    private final Notification anaThird = notify(world.ana.id());
    private final Notification beto = notify(world.beto.id());

    @Test
    void deletesOneOwnNotificationAndTheUnreadCountFollows() {
        inbox.delete(RankingFixture.user(world.ana), anaFirst.id());

        assertThat(notifications.of(world.ana.id())).extracting(Notification::id)
                .containsExactlyInAnyOrder(anaSecond.id(), anaThird.id());
        assertThat(inbox.list(RankingFixture.user(world.ana), 0, 20).unreadCount()).isEqualTo(2);
    }

    @Test
    void someoneElsesOrAMissingNotificationIsNotFound() {
        assertThatThrownBy(() -> inbox.delete(RankingFixture.user(world.ana), beto.id()))
                .isInstanceOf(NotFoundException.class).hasMessage("NOTIFICATION_NOT_FOUND");
        assertThatCode(() -> inbox.delete(RankingFixture.user(world.ana), anaFirst.id())).doesNotThrowAnyException();
        assertThatThrownBy(() -> inbox.delete(RankingFixture.user(world.ana), anaFirst.id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(notifications.of(world.beto.id())).hasSize(1);
    }

    @Test
    void batchDeletesOwnNotificationsAndIgnoresTheRest() {
        inbox.delete(RankingFixture.user(world.ana), List.of(anaFirst.id(), anaSecond.id(), beto.id(),
                UUID.randomUUID(), anaFirst.id()));

        assertThat(notifications.of(world.ana.id())).extracting(Notification::id).containsExactly(anaThird.id());
        assertThat(notifications.of(world.beto.id())).hasSize(1);
        // A resend succeeds.
        inbox.delete(RankingFixture.user(world.ana), List.of(anaFirst.id(), anaSecond.id()));
    }

    @Test
    void batchNeedsOneToOneHundredIds() {
        List<UUID> tooMany = IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID()).toList();
        List<UUID> withNull = new ArrayList<>(Arrays.asList(anaFirst.id(), null));
        for (List<UUID> ids : Arrays.asList(null, Collections.<UUID>emptyList(), tooMany, withNull)) {
            assertThatThrownBy(() -> inbox.delete(RankingFixture.user(world.ana), ids))
                    .isInstanceOf(RuleViolationException.class).hasMessage("NOTIFICATION_IDS_INVALID");
        }
        assertThat(notifications.of(world.ana.id())).hasSize(3);
        inbox.delete(RankingFixture.user(world.ana), tooMany.subList(0, 100));
    }

    @Test
    void deletesAllOwnNotificationsOnly() {
        inbox.deleteAll(RankingFixture.user(world.ana));
        inbox.deleteAll(RankingFixture.user(world.ana));

        assertThat(notifications.of(world.ana.id())).isEmpty();
        assertThat(inbox.list(RankingFixture.user(world.ana), 0, 20).unreadCount()).isZero();
        assertThat(notifications.of(world.beto.id())).hasSize(1);
    }

    private Notification notify(UUID recipient) {
        UUID id = UUID.randomUUID();
        Notification notification = new Notification(id, recipient, NotificationType.MATCH_REGISTERED, null,
                "Partida registrada", "Corpo", RankingFixture.CLOCK.instant(), null, "TEST:" + id);
        notifications.insertNew(List.of(notification));
        return notification;
    }
}
