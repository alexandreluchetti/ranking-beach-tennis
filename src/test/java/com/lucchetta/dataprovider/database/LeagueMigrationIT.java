package com.lucchetta.dataprovider.database;

import com.lucchetta.IntegrationTest;
import com.lucchetta.core.domain.league.DefaultLeague;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration V6 moves the existing group into the "Beach Tennis Arena" league (decision D-023);
 * V8 gives the older match notifications their league. Each scenario migrates a fresh database
 * up to the version before, adds data, then applies the rest.
 */
class LeagueMigrationIT extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-10T12:00:00Z");

    @Test
    void existingGroupMovesToTheArenaWithTheSystemAdministratorAsLeagueAdministrator() {
        JdbcTemplate db = databaseAtV5("v6_existing_group");
        UUID admin = insertUser(db, "admin-uid", T0.plusSeconds(60));
        UUID player = insertUser(db, "player-uid", T0);
        UUID season = insertSeason(db, player);
        UUID match = insertMatch(db, admin);

        migrateToLatest(db, "admin-uid");

        assertThat(db.queryForMap("select name, visibility, created_by, archived_at from league where id = ?",
                DefaultLeague.ID))
                .containsEntry("name", "Beach Tennis Arena")
                .containsEntry("visibility", "PUBLIC")
                .containsEntry("created_by", admin)
                .containsEntry("archived_at", null);
        assertThat(db.queryForObject("select count(*) from league", Integer.class)).isEqualTo(1);
        assertThat(roleOf(db, admin)).isEqualTo("ADMIN");
        assertThat(roleOf(db, player)).isEqualTo("MEMBER");
        assertThat(db.queryForObject("select joined_at from league_member where player_id = ?", Timestamp.class,
                player).toInstant()).isEqualTo(T0);
        assertThat(db.queryForObject("select league_id from season where id = ?", UUID.class, season))
                .isEqualTo(DefaultLeague.ID);
        assertThat(db.queryForObject("select league_id from match where id = ?", UUID.class, match))
                .isEqualTo(DefaultLeague.ID);
    }

    @Test
    void withoutTheSystemAdministratorTheFirstSeasonCreatorAdministersTheArena() {
        JdbcTemplate db = databaseAtV5("v6_season_creator");
        UUID oldest = insertUser(db, "oldest-uid", T0);
        UUID creator = insertUser(db, "creator-uid", T0.plusSeconds(60));
        insertSeason(db, creator);

        migrateToLatest(db, "unknown-uid");

        assertThat(roleOf(db, creator)).isEqualTo("ADMIN");
        assertThat(roleOf(db, oldest)).isEqualTo("MEMBER");
    }

    @Test
    void anEmptyDatabaseStillGetsTheArena() {
        JdbcTemplate db = databaseAtV5("v6_empty");

        migrateToLatest(db, "");

        assertThat(db.queryForObject("select created_by from league where id = ?", UUID.class, DefaultLeague.ID))
                .isNull();
        assertThat(db.queryForObject("select count(*) from league_member", Integer.class)).isZero();
    }

    @Test
    void olderMatchNotificationsGetTheLeagueOfTheirMatch() {
        JdbcTemplate db = databaseAtV5("v8_notifications");
        UUID player = insertUser(db, "player-uid", T0);
        UUID match = insertMatch(db, player);
        db.update("""
                insert into notification (id, recipient_id, type, match_id, title, body, created_at, dedup_key)
                values (?, ?, 'MATCH_REGISTERED', ?, 'Partida registrada', 'corpo', now(), 'REGISTERED:x')
                """, UUID.randomUUID(), player, match);

        migrateToLatest(db, "player-uid");

        assertThat(db.queryForObject("select league_id from notification", UUID.class)).isEqualTo(DefaultLeague.ID);
    }

    @Test
    void seasonsMayOverlapAcrossLeaguesButNotWithinOne() {
        UUID creator = insertUser(jdbcTemplate, "creator-uid", T0);
        UUID other = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into league (id, name, visibility, created_by, created_at, updated_at)
                values (?, 'Outra Liga', 'PRIVATE', ?, now(), now())
                """, other, creator);
        insertSeason(jdbcTemplate, DefaultLeague.ID, creator);

        insertSeason(jdbcTemplate, other, creator);

        assertThatThrownBy(() -> insertSeason(jdbcTemplate, other, creator))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_season_no_overlap");
    }

    @Test
    void leagueNamesAreUniqueIgnoringCase() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into league (id, name, visibility, created_at, updated_at)
                values (?, 'BEACH tennis ARENA', 'PUBLIC', now(), now())
                """, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_league_name");
    }

    private JdbcTemplate databaseAtV5(String name) {
        jdbcTemplate.execute("drop database if exists " + name);
        jdbcTemplate.execute("create database " + name);
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1"),
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        flyway(db, "").target("5").load().migrate();
        return db;
    }

    private static void migrateToLatest(JdbcTemplate db, String adminSubject) {
        flyway(db, adminSubject).load().migrate();
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(JdbcTemplate db,
                                                                                  String adminSubject) {
        return Flyway.configure()
                .dataSource(db.getDataSource())
                .locations("classpath:db/migration")
                .placeholders(Map.of("admin_auth_subject", adminSubject));
    }

    private static UUID insertUser(JdbcTemplate db, String subject, Instant createdAt) {
        UUID id = UUID.randomUUID();
        db.update("""
                insert into app_user (id, auth_provider, auth_subject, display_name, created_at, updated_at)
                values (?, 'firebase', ?, ?, ?, ?)
                """, id, subject, subject, Timestamp.from(createdAt), Timestamp.from(createdAt));
        return id;
    }

    private static UUID insertSeason(JdbcTemplate db, UUID createdBy) {
        UUID id = UUID.randomUUID();
        db.update("""
                insert into season (id, name, start_date, end_date, created_by, created_at, updated_at)
                values (?, 'Temporada', ?, ?, ?, now(), now())
                """, id, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), createdBy);
        return id;
    }

    private static void insertSeason(JdbcTemplate db, UUID leagueId, UUID createdBy) {
        db.update("""
                insert into season (id, league_id, name, start_date, end_date, created_by, created_at, updated_at)
                values (?, ?, 'Temporada', ?, ?, ?, now(), now())
                """, UUID.randomUUID(), leagueId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), createdBy);
    }

    private static UUID insertMatch(JdbcTemplate db, UUID registeredBy) {
        UUID id = UUID.randomUUID();
        db.update("""
                insert into match (id, played_at, format, team_a_games, team_b_games, status, registered_by,
                                   registered_at, updated_at)
                values (?, now(), 'STANDARD', 6, 4, 'VALID', ?, now(), now())
                """, id, registeredBy);
        return id;
    }

    private static String roleOf(JdbcTemplate db, UUID playerId) {
        return db.queryForObject("select role from league_member where league_id = ? and player_id = ?",
                String.class, DefaultLeague.ID, playerId);
    }
}
