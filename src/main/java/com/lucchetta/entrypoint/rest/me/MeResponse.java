package com.lucchetta.entrypoint.rest.me;

import com.lucchetta.core.domain.Player;

import java.util.UUID;

/**
 * @param displayName how the user wants to be called (nickname), shown in rankings and matches
 * @param fullName    first and last name; null until the user fills it in (the app asks for it)
 */
public record MeResponse(UUID id, String displayName, String fullName, String email, boolean admin) {

    static MeResponse from(Player player, boolean admin) {
        return new MeResponse(player.id(), player.displayName().value(),
                player.fullName() == null ? null : player.fullName().value(), player.email(), admin);
    }
}
