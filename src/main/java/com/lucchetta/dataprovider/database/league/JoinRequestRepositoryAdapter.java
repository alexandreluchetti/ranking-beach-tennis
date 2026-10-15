package com.lucchetta.dataprovider.database.league;

import com.lucchetta.core.domain.league.JoinRequest;
import com.lucchetta.core.domain.league.LeagueEntryStatus;
import com.lucchetta.core.port.JoinRequestRepository;
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
public class JoinRequestRepositoryAdapter implements JoinRequestRepository {

    private static final String SELECT_REQUEST = """
            select id, league_id, player_id, status, created_at, decided_by, decided_at
            from league_join_request
            """;

    private final JdbcClient jdbcClient;

    public JoinRequestRepositoryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void insert(JoinRequest request) {
        jdbcClient.sql("""
                        insert into league_join_request (id, league_id, player_id, status, created_at, decided_by,
                                                         decided_at)
                        values (:id, :leagueId, :playerId, :status, :createdAt, :decidedBy, :decidedAt)
                        """)
                .param("id", request.id())
                .param("leagueId", request.leagueId())
                .param("playerId", request.playerId())
                .param("status", request.status().name())
                .param("createdAt", utc(request.createdAt()))
                .param("decidedBy", request.decidedBy())
                .param("decidedAt", utc(request.decidedAt()))
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JoinRequest> findById(UUID id) {
        return jdbcClient.sql(SELECT_REQUEST + " where id = :id")
                .param("id", id)
                .query(JoinRequestRepositoryAdapter::mapRow)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JoinRequest> findPending(UUID leagueId, UUID playerId) {
        return jdbcClient.sql(SELECT_REQUEST
                        + " where league_id = :leagueId and player_id = :playerId and status = 'PENDING'")
                .param("leagueId", leagueId)
                .param("playerId", playerId)
                .query(JoinRequestRepositoryAdapter::mapRow)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JoinRequest> findPendingByLeague(UUID leagueId) {
        return jdbcClient.sql(SELECT_REQUEST
                        + " where league_id = :leagueId and status = 'PENDING' order by created_at, id")
                .param("leagueId", leagueId)
                .query(JoinRequestRepositoryAdapter::mapRow)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JoinRequest> findPendingByPlayer(UUID playerId) {
        return jdbcClient.sql(SELECT_REQUEST + " where player_id = :playerId and status = 'PENDING'")
                .param("playerId", playerId)
                .query(JoinRequestRepositoryAdapter::mapRow)
                .list();
    }

    @Override
    @Transactional
    public boolean decide(UUID id, LeagueEntryStatus status, UUID decidedBy, Instant decidedAt) {
        return jdbcClient.sql("""
                        update league_join_request
                        set status = :status, decided_by = :decidedBy, decided_at = :decidedAt
                        where id = :id and status = 'PENDING'
                        """)
                .param("id", id)
                .param("status", status.name())
                .param("decidedBy", decidedBy)
                .param("decidedAt", utc(decidedAt))
                .update() == 1;
    }

    @Override
    @Transactional
    public void cancelPending(UUID leagueId, UUID playerId, Instant at) {
        jdbcClient.sql("""
                        update league_join_request set status = 'CANCELLED', decided_at = :at
                        where league_id = :leagueId and player_id = :playerId and status = 'PENDING'
                        """)
                .param("leagueId", leagueId)
                .param("playerId", playerId)
                .param("at", utc(at))
                .update();
    }

    private static JoinRequest mapRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime decidedAt = rs.getObject("decided_at", OffsetDateTime.class);
        return new JoinRequest(
                rs.getObject("id", UUID.class),
                rs.getObject("league_id", UUID.class),
                rs.getObject("player_id", UUID.class),
                LeagueEntryStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("decided_by", UUID.class),
                decidedAt == null ? null : decidedAt.toInstant());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
