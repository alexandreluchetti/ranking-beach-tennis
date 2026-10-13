package com.lucchetta.core.port;

import com.lucchetta.core.domain.notification.DevicePlatform;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Push tokens of the players' devices. A token belongs to one player at a time: when someone
 * else signs in on the same phone, the token moves to them.
 */
public interface DeviceTokenRepository {

    void save(String token, UUID playerId, DevicePlatform platform, Instant now);

    void delete(String token, UUID playerId);

    /** Removes a token reported as invalid by the push service. */
    void deleteInvalid(String token);

    List<DeviceToken> findByPlayers(Collection<UUID> playerIds);

    record DeviceToken(String token, UUID playerId, DevicePlatform platform) {
    }
}
