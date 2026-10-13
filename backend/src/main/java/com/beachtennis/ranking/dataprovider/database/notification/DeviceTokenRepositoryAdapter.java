package com.beachtennis.ranking.dataprovider.database.notification;

import com.beachtennis.ranking.core.domain.notification.DevicePlatform;
import com.beachtennis.ranking.core.port.DeviceTokenRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
public class DeviceTokenRepositoryAdapter implements DeviceTokenRepository {

    private final JdbcClient jdbcClient;

    public DeviceTokenRepositoryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void save(String token, UUID playerId, DevicePlatform platform, Instant now) {
        jdbcClient.sql("""
                        insert into device_token (token, player_id, platform, updated_at)
                        values (:token, :playerId, :platform, :updatedAt)
                        on conflict (token) do update
                        set player_id = excluded.player_id, platform = excluded.platform, updated_at = excluded.updated_at
                        """)
                .param("token", token)
                .param("playerId", playerId)
                .param("platform", platform.name())
                .param("updatedAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
    }

    @Override
    @Transactional
    public void delete(String token, UUID playerId) {
        jdbcClient.sql("delete from device_token where token = :token and player_id = :playerId")
                .param("token", token)
                .param("playerId", playerId)
                .update();
    }

    @Override
    @Transactional
    public void deleteInvalid(String token) {
        jdbcClient.sql("delete from device_token where token = :token").param("token", token).update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceToken> findByPlayers(Collection<UUID> playerIds) {
        if (playerIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("select token, player_id, platform from device_token where player_id in (:ids)")
                .param("ids", playerIds)
                .query((rs, rowNum) -> new DeviceToken(rs.getString("token"), rs.getObject("player_id", UUID.class),
                        DevicePlatform.valueOf(rs.getString("platform"))))
                .list();
    }
}
