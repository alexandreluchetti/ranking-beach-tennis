package com.lucchetta.dataprovider.database.tournament;

import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.tournament.StageEntrant;
import com.lucchetta.core.domain.tournament.StageFixture;
import com.lucchetta.core.domain.tournament.StagePair;
import com.lucchetta.core.domain.tournament.StageRound;
import com.lucchetta.core.domain.tournament.TournamentStage;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.TournamentStageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Plain SQL persistence for the stage aggregate (stage, players, pairs and games). A change
 * replaces the players, pairs and games.
 */
@Component
public class TournamentStageRepositoryAdapter implements TournamentStageRepository {

    private static final String DAY_CONSTRAINT = "uk_tournament_stage_day";
    private static final String SELECT_STAGE = """
            select id, league_id, season_id, stage_date, format, created_by, created_at, updated_at, version
            from tournament_stage
            """;

    private final JdbcClient jdbcClient;

    public TournamentStageRepositoryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentStage> findById(UUID id) {
        return find(SELECT_STAGE + " where id = :id", id);
    }

    @Override
    @Transactional
    public Optional<TournamentStage> findByIdForUpdate(UUID id) {
        return find(SELECT_STAGE + " where id = :id for update", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentStage> findBySeason(UUID seasonId) {
        return jdbcClient.sql(SELECT_STAGE + " where season_id = :seasonId order by stage_date")
                .param("seasonId", seasonId)
                .query(TournamentStageRepositoryAdapter::mapRow)
                .list().stream()
                .map(this::withChildren)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsOn(UUID seasonId, LocalDate date) {
        return jdbcClient.sql("select count(*) > 0 from tournament_stage where season_id = :seasonId"
                        + " and stage_date = :date")
                .param("seasonId", seasonId)
                .param("date", date)
                .query(Boolean.class)
                .single();
    }

    @Override
    @Transactional
    public TournamentStage insert(TournamentStage stage) {
        try {
            jdbcClient.sql("""
                            insert into tournament_stage (id, league_id, season_id, stage_date, format, created_by,
                                                          created_at, updated_at, version)
                            values (:id, :leagueId, :seasonId, :date, :format, :createdBy, :createdAt, :updatedAt, 0)
                            """)
                    .param("id", stage.id())
                    .param("leagueId", stage.leagueId())
                    .param("seasonId", stage.seasonId())
                    .param("date", stage.date())
                    .param("format", stage.format().name())
                    .param("createdBy", stage.createdBy())
                    .param("createdAt", utc(stage.createdAt()))
                    .param("updatedAt", utc(stage.updatedAt()))
                    .update();
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(DAY_CONSTRAINT)) {
                throw new ConflictException("STAGE_ALREADY_EXISTS");
            }
            throw e;
        }
        insertChildren(stage);
        return findById(stage.id()).orElseThrow();
    }

    @Override
    @Transactional
    public TournamentStage update(TournamentStage stage) {
        int updated = jdbcClient.sql("""
                        update tournament_stage
                        set format = :format, updated_at = :updatedAt, version = version + 1
                        where id = :id and version = :version
                        """)
                .param("id", stage.id())
                .param("version", stage.version())
                .param("format", stage.format().name())
                .param("updatedAt", utc(stage.updatedAt()))
                .update();
        if (updated == 0) {
            throw new ConflictException("STAGE_MODIFIED");
        }
        deleteChildren(stage.id());
        insertChildren(stage);
        return findById(stage.id()).orElseThrow();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        boolean hasMatches = jdbcClient.sql("select count(*) > 0 from match where stage_id = :id")
                .param("id", id)
                .query(Boolean.class)
                .single();
        if (hasMatches) {
            throw new RuleViolationException("STAGE_STARTED");
        }
        jdbcClient.sql("delete from tournament_stage where id = :id").param("id", id).update();
    }

    private Optional<TournamentStage> find(String sql, UUID id) {
        return jdbcClient.sql(sql)
                .param("id", id)
                .query(TournamentStageRepositoryAdapter::mapRow)
                .optional()
                .map(this::withChildren);
    }

    private TournamentStage withChildren(StageRow row) {
        List<StageEntrant> entrants = jdbcClient.sql("""
                        select position, player_id, guest_name from tournament_stage_player
                        where stage_id = :id order by position
                        """)
                .param("id", row.id())
                .query((rs, n) -> new StageEntrant(rs.getInt("position"), rs.getObject("player_id", UUID.class),
                        rs.getString("guest_name")))
                .list();
        List<StagePair> pairs = jdbcClient.sql("""
                        select pair_number, group_number, first_position, second_position from tournament_pair
                        where stage_id = :id order by pair_number
                        """)
                .param("id", row.id())
                .query((rs, n) -> new StagePair(rs.getInt("pair_number"), rs.getInt("group_number"),
                        rs.getInt("first_position"), rs.getInt("second_position")))
                .list();
        List<StageFixture> fixtures = jdbcClient.sql("""
                        select fixture_number, round, group_number, pair_a, pair_b, match_id from tournament_fixture
                        where stage_id = :id order by fixture_number
                        """)
                .param("id", row.id())
                .query((rs, n) -> new StageFixture(rs.getInt("fixture_number"),
                        StageRound.valueOf(rs.getString("round")),
                        rs.getObject("group_number", Integer.class), rs.getInt("pair_a"), rs.getInt("pair_b"),
                        rs.getObject("match_id", UUID.class)))
                .list();
        return new TournamentStage(row.id(), row.leagueId(), row.seasonId(), row.date(), row.format(), entrants, pairs,
                new ArrayList<>(fixtures), row.createdBy(), row.createdAt(), row.updatedAt(), row.version());
    }

    private void deleteChildren(UUID stageId) {
        for (String table : List.of("tournament_fixture", "tournament_pair", "tournament_stage_player")) {
            jdbcClient.sql("delete from " + table + " where stage_id = :id").param("id", stageId).update();
        }
    }

    private void insertChildren(TournamentStage stage) {
        for (StageEntrant entrant : stage.entrants()) {
            jdbcClient.sql("""
                            insert into tournament_stage_player (stage_id, position, player_id, guest_name)
                            values (:stageId, :position, :playerId, :guestName)
                            """)
                    .param("stageId", stage.id())
                    .param("position", entrant.position())
                    .param("playerId", entrant.playerId())
                    .param("guestName", entrant.guestName())
                    .update();
        }
        for (StagePair pair : stage.pairs()) {
            jdbcClient.sql("""
                            insert into tournament_pair (stage_id, pair_number, group_number, first_position,
                                                         second_position)
                            values (:stageId, :number, :group, :first, :second)
                            """)
                    .param("stageId", stage.id())
                    .param("number", pair.number())
                    .param("group", pair.group())
                    .param("first", pair.firstPosition())
                    .param("second", pair.secondPosition())
                    .update();
        }
        for (StageFixture fixture : stage.fixtures()) {
            jdbcClient.sql("""
                            insert into tournament_fixture (stage_id, fixture_number, round, group_number, pair_a,
                                                            pair_b, match_id)
                            values (:stageId, :number, :round, :group, :pairA, :pairB, :matchId)
                            """)
                    .param("stageId", stage.id())
                    .param("number", fixture.number())
                    .param("round", fixture.round().name())
                    .param("group", fixture.group())
                    .param("pairA", fixture.pairA())
                    .param("pairB", fixture.pairB())
                    .param("matchId", fixture.matchId())
                    .update();
        }
    }

    private static StageRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new StageRow(
                rs.getObject("id", UUID.class),
                rs.getObject("league_id", UUID.class),
                rs.getObject("season_id", UUID.class),
                rs.getObject("stage_date", LocalDate.class),
                MatchFormat.valueOf(rs.getString("format")),
                rs.getObject("created_by", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                rs.getInt("version"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record StageRow(UUID id, UUID leagueId, UUID seasonId, LocalDate date, MatchFormat format, UUID createdBy,
                            Instant createdAt, Instant updatedAt, int version) {
    }
}
