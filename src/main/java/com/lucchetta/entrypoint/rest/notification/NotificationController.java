package com.lucchetta.entrypoint.rest.notification;

import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.domain.notification.DevicePlatform;
import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.domain.notification.NotificationType;
import com.lucchetta.core.usecase.notification.NotificationInbox;
import com.lucchetta.entrypoint.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The caller's own notifications and push devices.
 */
@RestController
public class NotificationController {

    /**
     * @param leagueId      league of a league notification
     * @param joinRequestId join request a league administrator may accept or reject
     * @param invitationId  invitation the recipient may accept or reject
     */
    public record NotificationResponse(UUID id, NotificationType type, UUID matchId, String title, String body,
                                       OffsetDateTime createdAt, boolean read, UUID leagueId, UUID joinRequestId,
                                       UUID invitationId) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.id(), n.type(), n.matchId(), n.title(), n.body(),
                    OffsetDateTime.ofInstant(n.createdAt(), BusinessCalendar.ZONE), n.read(), n.leagueId(),
                    n.joinRequestId(), n.invitationId());
        }
    }

    public record NotificationPageResponse(List<NotificationResponse> items, int unreadCount, int page, int size,
                                           boolean hasNext) {
    }

    public record DeviceRequest(String token, DevicePlatform platform) {
    }

    private final CurrentUserProvider currentUserProvider;
    private final NotificationInbox inbox;

    public NotificationController(CurrentUserProvider currentUserProvider, NotificationInbox inbox) {
        this.currentUserProvider = currentUserProvider;
        this.inbox = inbox;
    }

    @GetMapping("/api/notifications")
    @Operation(summary = "The caller's notifications, most recent first, with the unread count")
    public NotificationPageResponse list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        NotificationInbox.Page result = inbox.list(currentUserProvider.get(), page, size);
        return new NotificationPageResponse(result.items().stream().map(NotificationResponse::from).toList(),
                result.unreadCount(), result.page(), result.size(), result.hasNext());
    }

    @PostMapping("/api/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marks one of the caller's notifications as read")
    public void markRead(@PathVariable UUID id) {
        inbox.markRead(currentUserProvider.get(), id);
    }

    @PostMapping("/api/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marks all of the caller's notifications as read")
    public void markAllRead() {
        inbox.markAllRead(currentUserProvider.get());
    }

    @PutMapping("/api/me/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Registers or refreshes this device's push token for the caller")
    public void registerDevice(@RequestBody DeviceRequest request) {
        inbox.registerDevice(currentUserProvider.get(), request.token(), request.platform());
    }

    @DeleteMapping("/api/me/devices/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Stops push to this device (e.g. on sign-out)")
    public void unregisterDevice(@PathVariable String token) {
        inbox.unregisterDevice(currentUserProvider.get(), token);
    }
}
