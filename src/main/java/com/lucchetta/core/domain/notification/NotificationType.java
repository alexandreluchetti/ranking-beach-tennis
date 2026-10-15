package com.lucchetta.core.domain.notification;

public enum NotificationType {
    MATCH_REGISTERED,
    MATCH_CORRECTED,
    MATCH_VOIDED,
    MATCH_VOID_REVERTED,
    /** To league administrators: someone asked to join their league. */
    LEAGUE_JOIN_REQUESTED,
    /** To the requester. */
    LEAGUE_JOIN_REQUEST_ACCEPTED,
    /** To the requester. */
    LEAGUE_JOIN_REQUEST_REJECTED,
    /** To the invitee, who accepts or rejects it from the notification. */
    LEAGUE_INVITATION_RECEIVED
}
