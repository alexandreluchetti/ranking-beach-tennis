package com.lucchetta.dataprovider.database.league;

import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.port.LeagueMemberRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class LeagueMemberRepositoryAdapter implements LeagueMemberRepository {

    private static final String SELECT_MEMBER = "select league_id, player_id, role, joined_at from league_member";

    private final JdbcClient jdbcClient;

    public LeagueMemberRepositoryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LeagueMember> find(UUID leagueId, UUID playerId) {
        return jdbcClient.sql(SELECT_MEMBER + " where league_id = :leagueId and player_id = :playerId")
                .param("leagueId", leagueId)
                .param("playerId", playerId)
                .query(LeagueMemberRepositoryAdapter::mapRow)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeagueMember> findByLeague(UUID leagueId) {
        return jdbcClient.sql(SELECT_MEMBER + " where league_id = :leagueId")
                .param("leagueId", leagueId)
                .query(LeagueMemberRepositoryAdapter::mapRow)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeagueMember> findByPlayer(UUID playerId) {
        return jdbcClient.sql(SELECT_MEMBER + " where player_id = :playerId")
                .param("playerId", playerId)
                .query(LeagueMemberRepositoryAdapter::mapRow)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public int count(UUID leagueId) {
        return jdbcClient.sql("select count(*) from league_member where league_id = :leagueId")
                .param("leagueId", leagueId)
                .query(Integer.class)
                .single();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> countByLeagues(Collection<UUID> leagueIds) {
        Map<UUID, Integer> counts = new HashMap<>();
        if (leagueIds.isEmpty()) {
            return counts;
        }
        jdbcClient.sql("""
                        select league_id, count(*) as members from league_member
                        where league_id in (:ids) group by league_id
                        """)
                .param("ids", leagueIds)
                .query(rs -> {
                    counts.put(rs.getObject("league_id", UUID.class), rs.getInt("members"));
                });
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public int countAdmins(UUID leagueId) {
        return jdbcClient.sql("select count(*) from league_member where league_id = :leagueId and role = 'ADMIN'")
                .param("leagueId", leagueId)
                .query(Integer.class)
                .single();
    }

    @Override
    @Transactional
    public void insert(LeagueMember member) {
        jdbcClient.sql("""
                        insert into league_member (league_id, player_id, role, joined_at)
                        values (:leagueId, :playerId, :role, :joinedAt)
                        """)
                .param("leagueId", member.leagueId())
                .param("playerId", member.playerId())
                .param("role", member.role().name())
                .param("joinedAt", OffsetDateTime.ofInstant(member.joinedAt(), ZoneOffset.UTC))
                .update();
    }

    @Override
    @Transactional
    public void updateRole(UUID leagueId, UUID playerId, LeagueRole role) {
        jdbcClient.sql("update league_member set role = :role where league_id = :leagueId and player_id = :playerId")
                .param("leagueId", leagueId)
                .param("playerId", playerId)
                .param("role", role.name())
                .update();
    }

    @Override
    @Transactional
    public void delete(UUID leagueId, UUID playerId) {
        jdbcClient.sql("delete from league_member where league_id = :leagueId and player_id = :playerId")
                .param("leagueId", leagueId)
                .param("playerId", playerId)
                .update();
    }

    private static LeagueMember mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LeagueMember(
                rs.getObject("league_id", UUID.class),
                rs.getObject("player_id", UUID.class),
                LeagueRole.valueOf(rs.getString("role")),
                rs.getObject("joined_at", OffsetDateTime.class).toInstant());
    }
}
