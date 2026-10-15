package com.lucchetta.dataprovider.database.league;

import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueVisibility;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.port.LeagueRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class LeagueRepositoryAdapter implements LeagueRepository {

    private static final String NAME_CONSTRAINT = "ux_league_name";
    private static final String SELECT_LEAGUE = """
            select id, name, description, visibility, invite_code, created_by, created_at, updated_at,
                   archived_at, archived_by, version
            from league
            """;

    private final JdbcClient jdbcClient;

    public LeagueRepositoryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<League> findById(UUID id) {
        return jdbcClient.sql(SELECT_LEAGUE + " where id = :id")
                .param("id", id)
                .query(LeagueRepositoryAdapter::mapRow)
                .optional();
    }

    @Override
    @Transactional
    public Optional<League> findByIdForUpdate(UUID id) {
        return jdbcClient.sql(SELECT_LEAGUE + " where id = :id for update")
                .param("id", id)
                .query(LeagueRepositoryAdapter::mapRow)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<League> findAllByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(SELECT_LEAGUE + " where id in (:ids)")
                .param("ids", ids)
                .query(LeagueRepositoryAdapter::mapRow)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<League> findByInviteCode(String inviteCode) {
        return jdbcClient.sql(SELECT_LEAGUE + " where invite_code = :inviteCode")
                .param("inviteCode", inviteCode)
                .query(LeagueRepositoryAdapter::mapRow)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(String name, UUID excludedId) {
        return jdbcClient.sql("""
                        select count(*) > 0 from league
                        where lower(name) = lower(:name) and (cast(:excludedId as uuid) is null or id <> :excludedId)
                        """)
                .param("name", name)
                .param("excludedId", excludedId)
                .query(Boolean.class)
                .single();
    }

    @Override
    @Transactional(readOnly = true)
    public int countActiveCreatedBy(UUID playerId) {
        return jdbcClient.sql("select count(*) from league where created_by = :playerId and archived_at is null")
                .param("playerId", playerId)
                .query(Integer.class)
                .single();
    }

    @Override
    @Transactional(readOnly = true)
    public List<League> search(String nameFragment, boolean archived, int offset, int limit) {
        boolean filtered = nameFragment != null && !nameFragment.isBlank();
        String sql = SELECT_LEAGUE
                + " where archived_at is " + (archived ? "not null" : "null")
                + (filtered ? " and lower(name) like :pattern escape '\\'" : "")
                + " order by lower(name) offset :offset limit :limit";
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql)
                .param("offset", offset)
                .param("limit", limit);
        if (filtered) {
            String pattern = "%" + escapeLike(nameFragment.strip().toLowerCase(Locale.ROOT)) + "%";
            statement = statement.param("pattern", pattern);
        }
        return statement.query(LeagueRepositoryAdapter::mapRow).list();
    }

    @Override
    @Transactional
    public League insert(League league) {
        translatingNameConflict(() -> jdbcClient.sql("""
                        insert into league (id, name, description, visibility, invite_code, created_by, created_at,
                                            updated_at, archived_at, archived_by, version)
                        values (:id, :name, :description, :visibility, :inviteCode, :createdBy, :createdAt,
                                :updatedAt, :archivedAt, :archivedBy, 0)
                        """)
                .param("id", league.id())
                .param("name", league.name())
                .param("description", league.description())
                .param("visibility", league.visibility().name())
                .param("inviteCode", league.inviteCode())
                .param("createdBy", league.createdBy())
                .param("createdAt", utc(league.createdAt()))
                .param("updatedAt", utc(league.updatedAt()))
                .param("archivedAt", utc(league.archivedAt()))
                .param("archivedBy", league.archivedBy())
                .update());
        return findById(league.id()).orElseThrow();
    }

    @Override
    @Transactional
    public League update(League league) {
        int updated = translatingNameConflict(() -> jdbcClient.sql("""
                        update league
                        set name = :name, description = :description, visibility = :visibility,
                            invite_code = :inviteCode, updated_at = :updatedAt, archived_at = :archivedAt,
                            archived_by = :archivedBy, version = version + 1
                        where id = :id and version = :version
                        """)
                .param("id", league.id())
                .param("version", league.version())
                .param("name", league.name())
                .param("description", league.description())
                .param("visibility", league.visibility().name())
                .param("inviteCode", league.inviteCode())
                .param("updatedAt", utc(league.updatedAt()))
                .param("archivedAt", utc(league.archivedAt()))
                .param("archivedBy", league.archivedBy())
                .update());
        if (updated == 0) {
            throw new ConflictException("LEAGUE_MODIFIED");
        }
        return findById(league.id()).orElseThrow();
    }

    /** The unique index is the final guard against two leagues with the same name. */
    private static int translatingNameConflict(Supplier<Integer> write) {
        try {
            return write.get();
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(NAME_CONSTRAINT)) {
                throw new ConflictException("LEAGUE_NAME_TAKEN");
            }
            throw e;
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static League mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new League(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                LeagueVisibility.valueOf(rs.getString("visibility")),
                rs.getString("invite_code"),
                rs.getObject("created_by", UUID.class),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "archived_at"),
                rs.getObject("archived_by", UUID.class),
                rs.getInt("version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
