package com.lucchetta.dataprovider.database.league;

import com.lucchetta.core.domain.league.LeagueEntryStatus;
import com.lucchetta.core.domain.league.LeagueInvitation;
import com.lucchetta.core.port.LeagueInvitationRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class LeagueInvitationRepositoryAdapter implements LeagueInvitationRepository {

    private static final String SELECT_INVITATION = """
            select id, league_id, player_id, invited_by, status, created_at, decided_at
            from league_invitation
            """;

    private final JdbcClient jdbcClient;

    public LeagueInvitationRepositoryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void insert(LeagueInvitation invitation) {
        jdbcClient.sql("""
                        insert into league_invitation (id, league_id, player_id, invited_by, status, created_at,
                                                       decided_at)
                        values (:id, :leagueId, :playerId, :invitedBy, :status, :createdAt, :decidedAt)
                        """)
                .param("id", invitation.id())
                .param("leagueId", invitation.leagueId())
                .param("playerId", invitation.playerId())
                .param("invitedBy", invitation.invitedBy())
                .param("status", invitation.status().name())
                .param("createdAt", utc(invitation.createdAt()))
                .param("decidedAt", utc(invitation.decidedAt()))
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LeagueInvitation> findById(UUID id) {
        return jdbcClient.sql(SELECT_INVITATION + " where id = :id")
                .param("id", id)
                .query(LeagueInvitationRepositoryAdapter::mapRow)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LeagueInvitation> findPending(UUID leagueId, UUID playerId) {
        return jdbcClient.sql(SELECT_INVITATION
                        + " where league_id = :leagueId and player_id = :playerId and status = 'PENDING'")
                .param("leagueId", leagueId)
                .param("playerId", playerId)
                .query(LeagueInvitationRepositoryAdapter::mapRow)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeagueInvitation> findPendingByPlayer(UUID playerId) {
        return jdbcClient.sql(SELECT_INVITATION
                        + " where player_id = :playerId and status = 'PENDING' order by created_at desc, id")
                .param("playerId", playerId)
                .query(LeagueInvitationRepositoryAdapter::mapRow)
                .list();
    }

    @Override
    @Transactional
    public boolean decide(UUID id, LeagueEntryStatus status, Instant decidedAt) {
        return jdbcClient.sql("""
                        update league_invitation set status = :status, decided_at = :decidedAt
                        where id = :id and status = 'PENDING'
                        """)
                .param("id", id)
                .param("status", status.name())
                .param("decidedAt", utc(decidedAt))
                .update() == 1;
    }

    @Override
    @Transactional
    public void cancelPending(UUID leagueId, UUID playerId, Instant at) {
        jdbcClient.sql("""
                        update league_invitation set status = 'CANCELLED', decided_at = :at
                        where league_id = :leagueId and player_id = :playerId and status = 'PENDING'
                        """)
                .param("leagueId", leagueId)
                .param("playerId", playerId)
                .param("at", utc(at))
                .update();
    }

    private static LeagueInvitation mapRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime decidedAt = rs.getObject("decided_at", OffsetDateTime.class);
        return new LeagueInvitation(
                rs.getObject("id", UUID.class),
                rs.getObject("league_id", UUID.class),
                rs.getObject("player_id", UUID.class),
                rs.getObject("invited_by", UUID.class),
                LeagueEntryStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                decidedAt == null ? null : decidedAt.toInstant());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
