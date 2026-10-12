package com.beachtennis.ranking.entrypoint.rest.me;

import com.beachtennis.ranking.core.domain.Player;

import java.util.UUID;

public record MeResponse(UUID id, String displayName, String email, boolean admin) {

    static MeResponse from(Player player, boolean admin) {
        return new MeResponse(player.id(), player.displayName().value(), player.email(), admin);
    }
}
