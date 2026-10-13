package com.lucchetta.entrypoint.rest.match;

import java.util.UUID;

/**
 * Either a registered player ({@code playerId}) or a guest ({@code guest = true}, optional
 * {@code guestName}).
 */
public record ParticipantRequest(UUID playerId, Boolean guest, String guestName) {

    public boolean isGuest() {
        return Boolean.TRUE.equals(guest);
    }
}
