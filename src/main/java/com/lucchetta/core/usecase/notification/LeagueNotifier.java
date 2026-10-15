package com.lucchetta.core.usecase.notification;

import com.lucchetta.core.domain.league.JoinRequest;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueInvitation;
import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.domain.notification.NotificationType;
import com.lucchetta.core.port.NotificationRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * In-app notifications about joining a league (decision D-019). Texts are pt-BR, written here so
 * in-app and push match. Each event has a stable key, so it is never notified twice.
 */
public class LeagueNotifier {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public LeagueNotifier(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    /** To the league administrators. */
    public List<Notification> joinRequested(JoinRequest request, League league, String requesterName,
                                            Collection<UUID> administratorIds) {
        Instant now = clock.instant();
        String body = requesterName + " pediu para entrar na liga " + league.name() + ".";
        return notificationRepository.insertNew(administratorIds.stream()
                .map(recipient -> new Notification(UUID.randomUUID(), recipient,
                        NotificationType.LEAGUE_JOIN_REQUESTED, null, "Pedido de entrada", body, now, null,
                        "JOIN_REQUESTED:" + request.id() + ":" + recipient, league.id(), request.id(), null))
                .toList());
    }

    /** To the requester. */
    public List<Notification> joinRequestAnswered(JoinRequest request, League league, boolean accepted) {
        String title = accepted ? "Pedido aceito" : "Pedido recusado";
        String body = "Seu pedido para entrar na liga " + league.name() + " foi "
                + (accepted ? "aceito." : "recusado.");
        NotificationType type = accepted ? NotificationType.LEAGUE_JOIN_REQUEST_ACCEPTED
                : NotificationType.LEAGUE_JOIN_REQUEST_REJECTED;
        return notificationRepository.insertNew(List.of(new Notification(UUID.randomUUID(), request.playerId(), type,
                null, title, body, clock.instant(), null, type + ":" + request.id() + ":" + request.playerId(),
                league.id(), request.id(), null)));
    }

    /** To the invitee, who accepts or rejects it from the notification. */
    public List<Notification> invitationReceived(LeagueInvitation invitation, League league, String inviterName) {
        String body = inviterName + " convidou você para a liga " + league.name() + ".";
        return notificationRepository.insertNew(List.of(new Notification(UUID.randomUUID(), invitation.playerId(),
                NotificationType.LEAGUE_INVITATION_RECEIVED, null, "Convite para liga", body, clock.instant(), null,
                "INVITATION:" + invitation.id() + ":" + invitation.playerId(), league.id(), null, invitation.id())));
    }
}
