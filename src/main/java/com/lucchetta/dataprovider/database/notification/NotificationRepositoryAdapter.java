package com.lucchetta.dataprovider.database.notification;

import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.domain.notification.NotificationType;
import com.lucchetta.core.port.NotificationRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final JdbcClient jdbcClient;

    public NotificationRepositoryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public List<Notification> insertNew(List<Notification> notifications) {
        List<Notification> inserted = new ArrayList<>();
        for (Notification n : notifications) {
            int rows = jdbcClient.sql("""
                            insert into notification (id, recipient_id, type, match_id, title, body, created_at,
                                                      dedup_key, league_id, join_request_id, invitation_id)
                            values (:id, :recipientId, :type, :matchId, :title, :body, :createdAt, :dedupKey,
                                    :leagueId, :joinRequestId, :invitationId)
                            on conflict (dedup_key) do nothing
                            """)
                    .param("id", n.id())
                    .param("recipientId", n.recipientId())
                    .param("type", n.type().name())
                    .param("matchId", n.matchId())
                    .param("title", n.title())
                    .param("body", n.body())
                    .param("createdAt", utc(n.createdAt()))
                    .param("dedupKey", n.dedupKey())
                    .param("leagueId", n.leagueId())
                    .param("joinRequestId", n.joinRequestId())
                    .param("invitationId", n.invitationId())
                    .update();
            if (rows == 1) {
                inserted.add(n);
            }
        }
        return inserted;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findByRecipient(UUID recipientId, int offset, int limit) {
        return jdbcClient.sql("""
                        select id, recipient_id, type, match_id, title, body, created_at, read_at, dedup_key,
                               league_id, join_request_id, invitation_id
                        from notification where recipient_id = :recipientId
                        order by created_at desc, id
                        offset :offset limit :limit
                        """)
                .param("recipientId", recipientId)
                .param("offset", offset)
                .param("limit", limit)
                .query((rs, rowNum) -> {
                    OffsetDateTime readAt = rs.getObject("read_at", OffsetDateTime.class);
                    return new Notification(
                            rs.getObject("id", UUID.class),
                            rs.getObject("recipient_id", UUID.class),
                            NotificationType.valueOf(rs.getString("type")),
                            rs.getObject("match_id", UUID.class),
                            rs.getString("title"),
                            rs.getString("body"),
                            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                            readAt == null ? null : readAt.toInstant(),
                            rs.getString("dedup_key"),
                            rs.getObject("league_id", UUID.class),
                            rs.getObject("join_request_id", UUID.class),
                            rs.getObject("invitation_id", UUID.class));
                })
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public int countUnread(UUID recipientId) {
        return jdbcClient.sql("select count(*) from notification where recipient_id = :recipientId and read_at is null")
                .param("recipientId", recipientId)
                .query(Integer.class)
                .single();
    }

    @Override
    @Transactional
    public boolean markRead(UUID notificationId, UUID recipientId, Instant readAt) {
        return jdbcClient.sql("""
                        update notification set read_at = coalesce(read_at, :readAt)
                        where id = :id and recipient_id = :recipientId
                        """)
                .param("id", notificationId)
                .param("recipientId", recipientId)
                .param("readAt", utc(readAt))
                .update() == 1;
    }

    @Override
    @Transactional
    public void markAllRead(UUID recipientId, Instant readAt) {
        jdbcClient.sql("update notification set read_at = :readAt where recipient_id = :recipientId and read_at is null")
                .param("recipientId", recipientId)
                .param("readAt", utc(readAt))
                .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
