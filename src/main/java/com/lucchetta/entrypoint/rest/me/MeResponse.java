package com.lucchetta.entrypoint.rest.me;

import com.lucchetta.core.domain.Player;

import java.util.UUID;

public record MeResponse(UUID id, String displayName, String email, boolean admin) {

    static MeResponse from(Player player, boolean admin) {
        return new MeResponse(player.id(), player.displayName().value(), player.email(), admin);
    }
}
