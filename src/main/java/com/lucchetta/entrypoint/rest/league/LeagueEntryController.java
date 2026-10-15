package com.lucchetta.entrypoint.rest.league;

import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.usecase.league.InviteCodes;
import com.lucchetta.core.usecase.league.JoinRequests;
import com.lucchetta.core.usecase.league.LeagueInvitations;
import com.lucchetta.core.usecase.league.LeagueView;
import com.lucchetta.entrypoint.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Joining a private league (decision D-019): join requests, in-app invitations and invite codes.
 */
@RestController
public class LeagueEntryController {

    public record JoinRequestResponse(UUID id, UUID playerId, String displayName, OffsetDateTime createdAt) {
    }

    public record InviteRequest(String email) {
    }

    public record InvitationResponse(UUID id, LeagueResponse league, String invitedByName, OffsetDateTime createdAt) {
    }

    /** @param code null while no code was generated */
    public record InviteCodeResponse(String code) {
    }

    public record JoinByCodeRequest(String code) {
    }

    private final CurrentUserProvider currentUserProvider;
    private final JoinRequests joinRequests;
    private final LeagueInvitations invitations;
    private final InviteCodes inviteCodes;

    public LeagueEntryController(CurrentUserProvider currentUserProvider,
                                 JoinRequests joinRequests,
                                 LeagueInvitations invitations,
                                 InviteCodes inviteCodes) {
        this.currentUserProvider = currentUserProvider;
        this.joinRequests = joinRequests;
        this.invitations = invitations;
        this.inviteCodes = inviteCodes;
    }

    @PostMapping("/api/leagues/{id}/join-requests")
    @Operation(summary = "Asks to join a private league; its administrators are notified")
    public LeagueResponse requestToJoin(@PathVariable UUID id) {
        return LeagueResponse.from(joinRequests.request(currentUserProvider.get(), id));
    }

    @DeleteMapping("/api/leagues/{id}/join-requests/mine")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancels the caller's pending join request")
    public void cancelJoinRequest(@PathVariable UUID id) {
        joinRequests.cancel(currentUserProvider.get(), id);
    }

    @GetMapping("/api/leagues/{id}/join-requests")
    @Operation(summary = "Pending join requests, oldest first (league administrator)")
    public List<JoinRequestResponse> pendingJoinRequests(@PathVariable UUID id) {
        return joinRequests.pending(currentUserProvider.get(), id).stream()
                .map(pending -> new JoinRequestResponse(pending.request().id(), pending.request().playerId(),
                        pending.displayName(), local(pending.request().createdAt())))
                .toList();
    }

    @PostMapping("/api/leagues/{id}/join-requests/{requestId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Accepts a join request; the requester becomes a member (league administrator)")
    public void acceptJoinRequest(@PathVariable UUID id, @PathVariable UUID requestId) {
        joinRequests.accept(currentUserProvider.get(), id, requestId);
    }

    @PostMapping("/api/leagues/{id}/join-requests/{requestId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Rejects a join request (league administrator)")
    public void rejectJoinRequest(@PathVariable UUID id, @PathVariable UUID requestId) {
        joinRequests.reject(currentUserProvider.get(), id, requestId);
    }

    @PostMapping("/api/leagues/{id}/invitations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Invites the account with this exact e-mail (league administrator). "
            + "The answer is the same whether or not such an account exists.")
    public void invite(@PathVariable UUID id, @RequestBody InviteRequest request) {
        invitations.invite(currentUserProvider.get(), id, request.email());
    }

    @GetMapping("/api/me/invitations")
    @Operation(summary = "The caller's pending league invitations, most recent first")
    public List<InvitationResponse> myInvitations() {
        return invitations.received(currentUserProvider.get()).stream()
                .map(received -> new InvitationResponse(received.invitation().id(),
                        LeagueResponse.from(new LeagueView(received.league(), received.memberCount(), null, false,
                                false)),
                        received.invitedByName(), local(received.invitation().createdAt())))
                .toList();
    }

    @PostMapping("/api/me/invitations/{invitationId}/accept")
    @Operation(summary = "Accepts an invitation and joins the league")
    public LeagueResponse acceptInvitation(@PathVariable UUID invitationId) {
        return LeagueResponse.from(invitations.accept(currentUserProvider.get(), invitationId));
    }

    @PostMapping("/api/me/invitations/{invitationId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Rejects an invitation")
    public void rejectInvitation(@PathVariable UUID invitationId) {
        invitations.reject(currentUserProvider.get(), invitationId);
    }

    @GetMapping("/api/leagues/{id}/invite-code")
    @Operation(summary = "Current invite code, null if none (league administrator)")
    public InviteCodeResponse inviteCode(@PathVariable UUID id) {
        return new InviteCodeResponse(inviteCodes.current(currentUserProvider.get(), id));
    }

    @PostMapping("/api/leagues/{id}/invite-code")
    @Operation(summary = "Generates a new invite code; the previous one stops working (league administrator)")
    public InviteCodeResponse regenerateInviteCode(@PathVariable UUID id) {
        return new InviteCodeResponse(inviteCodes.regenerate(currentUserProvider.get(), id));
    }

    @PostMapping("/api/leagues/join-by-code")
    @Operation(summary = "Joins the league of an invite code, private or not")
    public LeagueResponse joinByCode(@RequestBody JoinByCodeRequest request) {
        return LeagueResponse.from(inviteCodes.join(currentUserProvider.get(), request.code()));
    }

    private static OffsetDateTime local(Instant instant) {
        return OffsetDateTime.ofInstant(instant, BusinessCalendar.ZONE);
    }
}
