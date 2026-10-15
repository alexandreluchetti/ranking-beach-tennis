package com.lucchetta.core.usecase.notification;

import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.match.MatchAuditEntry;
import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.domain.notification.NotificationType;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.NotificationRepository;
import com.lucchetta.core.usecase.match.MatchDetails;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Creates the in-app notifications of match events. Recipients: the registered participants
 * (before and after a correction, D-015) and the administrators of the match's league (D-021).
 * Guests are never notified.
 * Each event has a stable key, so retries, resends and repeated syncs never notify twice.
 */
public class MatchNotifier {

    private final NotificationRepository notificationRepository;
    private final LeagueMemberRepository memberRepository;
    private final MatchNotificationComposer composer;
    private final Clock clock;

    public MatchNotifier(NotificationRepository notificationRepository,
                         LeagueMemberRepository memberRepository,
                         MatchNotificationComposer composer,
                         Clock clock) {
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
        this.composer = composer;
        this.clock = clock;
    }

    /** @return the notifications stored now (empty if this event was already notified) */
    public List<Notification> registered(MatchDetails details) {
        UUID matchId = details.match().id();
        String eventKey = "REGISTERED:" + matchId;
        Instant now = clock.instant();
        return notificationRepository.insertNew(recipients(details, details).stream()
                .map(recipient -> {
                    MatchNotificationComposer.Message message = composer.registered(details, recipient);
                    return notification(recipient, NotificationType.MATCH_REGISTERED, details, message, now, eventKey);
                })
                .toList());
    }

    /** @return the notifications stored now (empty if this event was already notified) */
    public List<Notification> changed(MatchAuditEntry.Action action, MatchDetails before, MatchDetails after,
                                      String reason) {
        UUID matchId = after.match().id();
        String eventKey = action + ":" + matchId + ":v" + after.match().version();
        NotificationType type = switch (action) {
            case CORRECTED -> NotificationType.MATCH_CORRECTED;
            case VOIDED -> NotificationType.MATCH_VOIDED;
            case VOID_REVERTED -> NotificationType.MATCH_VOID_REVERTED;
        };
        Instant now = clock.instant();
        return notificationRepository.insertNew(recipients(before, after).stream()
                .map(recipient -> notification(recipient, type, after,
                        composer.changed(action, before, after, reason, recipient), now, eventKey))
                .toList());
    }

    private Set<UUID> recipients(MatchDetails before, MatchDetails after) {
        Set<UUID> recipients = new LinkedHashSet<>(before.match().playerIds());
        recipients.addAll(after.match().playerIds());
        memberRepository.findByLeague(after.match().leagueId()).stream()
                .filter(LeagueMember::admin)
                .map(LeagueMember::playerId)
                .forEach(recipients::add);
        return recipients;
    }

    private static Notification notification(UUID recipient, NotificationType type, MatchDetails details,
                                             MatchNotificationComposer.Message message, Instant now, String eventKey) {
        return new Notification(UUID.randomUUID(), recipient, type, details.match().id(), message.title(),
                message.body(), now, null, eventKey + ":" + recipient, details.match().leagueId(), null, null);
    }
}
