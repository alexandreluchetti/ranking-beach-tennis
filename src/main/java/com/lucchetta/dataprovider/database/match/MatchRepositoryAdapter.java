package com.lucchetta.dataprovider.database.match;

import com.lucchetta.core.domain.league.DefaultLeague;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.domain.match.Score;
import com.lucchetta.core.domain.match.Team;
import com.lucchetta.core.domain.match.VoidInfo;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.port.MatchRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Plain SQL persistence for the match aggregate (match + four participants).
 */
@Component
public class MatchRepositoryAdapter implements MatchRepository {

    private static final String SELECT_MATCH = """
            select id, played_at, format, team_a_games, team_b_games, tiebreak_team_a_points,
                   tiebreak_team_b_points, status, registered_by, registered_at, updated_at,
                   void_reason, voided_by, voided_at, version
            from match
            """;

    private final JdbcClient jdbcClient;

    public MatchRepositoryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Match> findById(UUID id) {
        List<MatchRow> rows = jdbcClient.sql(SELECT_MATCH + " where id = :id")
                .param("id", id)
                .query(MatchRepositoryAdapter::mapRow)
                .list();
        return assemble(rows).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Match> findValidPlayedBetween(Instant from, Instant to) {
        List<MatchRow> rows = jdbcClient.sql(SELECT_MATCH
                        + " where status = 'VALID' and played_at >= :from and played_at < :to order by played_at")
                .param("from", utc(from))
                .param("to", utc(to))
                .query(MatchRepositoryAdapter::mapRow)
                .list();
        return assemble(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Match> findPlayed(Instant from, Instant to, UUID playerId) {
        StringBuilder sql = new StringBuilder(SELECT_MATCH).append(" where true");
        Map<String, Object> params = new HashMap<>();
        if (from != null) {
            sql.append(" and played_at >= :from");
            params.put("from", utc(from));
        }
        if (to != null) {
            sql.append(" and played_at < :to");
            params.put("to", utc(to));
        }
        if (playerId != null) {
            sql.append(" and exists (select 1 from match_participant mp where mp.match_id = match.id"
                    + " and mp.player_id = :playerId)");
            params.put("playerId", playerId);
        }
        sql.append(" order by played_at desc, registered_at desc");
        return assemble(jdbcClient.sql(sql.toString()).params(params).query(MatchRepositoryAdapter::mapRow).list());
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(Match match) {
        int inserted = jdbcClient.sql("""
                        insert into match (id, league_id, played_at, format, team_a_games, team_b_games,
                                           tiebreak_team_a_points, tiebreak_team_b_points, status,
                                           registered_by, registered_at, updated_at)
                        values (:id, :leagueId, :playedAt, :format, :teamAGames, :teamBGames, :tiebreakA, :tiebreakB,
                                :status, :registeredBy, :registeredAt, :updatedAt)
                        on conflict (id) do nothing
                        """)
                .param("id", match.id())
                .param("leagueId", DefaultLeague.ID)
                .param("playedAt", utc(match.playedAt()))
                .param("format", match.format().name())
                .param("teamAGames", match.score().teamAGames())
                .param("teamBGames", match.score().teamBGames())
                .param("tiebreakA", match.score().tiebreakTeamAPoints())
                .param("tiebreakB", match.score().tiebreakTeamBPoints())
                .param("status", match.status().name())
                .param("registeredBy", match.registeredBy())
                .param("registeredAt", utc(match.registeredAt()))
                .param("updatedAt", utc(match.updatedAt()))
                .update();
        if (inserted == 0) {
            return false;
        }
        insertParticipants(match);
        return true;
    }

    @Override
    @Transactional
    public Match update(Match match) {
        VoidInfo voiding = match.voiding();
        int updated = jdbcClient.sql("""
                        update match
                        set played_at = :playedAt, format = :format, team_a_games = :teamAGames,
                            team_b_games = :teamBGames, tiebreak_team_a_points = :tiebreakA,
                            tiebreak_team_b_points = :tiebreakB, status = :status, updated_at = :updatedAt,
                            void_reason = :voidReason, voided_by = :voidedBy, voided_at = :voidedAt,
                            version = version + 1
                        where id = :id and version = :version
                        """)
                .param("id", match.id())
                .param("version", match.version())
                .param("playedAt", utc(match.playedAt()))
                .param("format", match.format().name())
                .param("teamAGames", match.score().teamAGames())
                .param("teamBGames", match.score().teamBGames())
                .param("tiebreakA", match.score().tiebreakTeamAPoints())
                .param("tiebreakB", match.score().tiebreakTeamBPoints())
                .param("status", match.status().name())
                .param("updatedAt", utc(match.updatedAt()))
                .param("voidReason", voiding == null ? null : voiding.reason())
                .param("voidedBy", voiding == null ? null : voiding.voidedBy())
                .param("voidedAt", voiding == null ? null : utc(voiding.voidedAt()))
                .update();
        if (updated == 0) {
            throw new ConflictException("MATCH_MODIFIED");
        }
        jdbcClient.sql("delete from match_participant where match_id = :id").param("id", match.id()).update();
        insertParticipants(match);
        return findById(match.id()).orElseThrow();
    }

    private void insertParticipants(Match match) {
        for (Participant p : match.participants()) {
            jdbcClient.sql("""
                            insert into match_participant (match_id, team, slot, player_id, guest_name)
                            values (:matchId, :team, :slot, :playerId, :guestName)
                            """)
                    .param("matchId", match.id())
                    .param("team", p.team().name())
                    .param("slot", p.slot())
                    .param("playerId", p.playerId())
                    .param("guestName", p.guestName())
                    .update();
        }
    }

    private List<Match> assemble(List<MatchRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Participant>> participants = participantsOf(rows.stream().map(MatchRow::id).toList());
        List<Match> matches = new ArrayList<>(rows.size());
        for (MatchRow row : rows) {
            matches.add(new Match(row.id(), row.playedAt(), row.score(), participants.get(row.id()), row.status(),
                    row.registeredBy(), row.registeredAt(), row.updatedAt(), row.voiding(), row.version()));
        }
        return matches;
    }

    private Map<UUID, List<Participant>> participantsOf(Collection<UUID> matchIds) {
        Map<UUID, List<Participant>> result = new HashMap<>();
        jdbcClient.sql("""
                        select match_id, team, slot, player_id, guest_name
                        from match_participant where match_id in (:ids)
                        """)
                .param("ids", matchIds)
                .query(rs -> {
                    result.computeIfAbsent(rs.getObject("match_id", UUID.class), k -> new ArrayList<>())
                            .add(new Participant(Team.valueOf(rs.getString("team")), rs.getInt("slot"),
                                    rs.getObject("player_id", UUID.class), rs.getString("guest_name")));
                });
        return result;
    }

    private static MatchRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Score score = new Score(
                MatchFormat.valueOf(rs.getString("format")),
                rs.getInt("team_a_games"),
                rs.getInt("team_b_games"),
                rs.getObject("tiebreak_team_a_points", Integer.class),
                rs.getObject("tiebreak_team_b_points", Integer.class));
        String voidReason = rs.getString("void_reason");
        VoidInfo voiding = voidReason == null ? null
                : new VoidInfo(voidReason, rs.getObject("voided_by", UUID.class), instant(rs, "voided_at"));
        return new MatchRow(
                rs.getObject("id", UUID.class),
                instant(rs, "played_at"),
                score,
                MatchStatus.valueOf(rs.getString("status")),
                rs.getObject("registered_by", UUID.class),
                instant(rs, "registered_at"),
                instant(rs, "updated_at"),
                voiding,
                rs.getInt("version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record MatchRow(UUID id, Instant playedAt, Score score, MatchStatus status, UUID registeredBy,
                            Instant registeredAt, Instant updatedAt, VoidInfo voiding, int version) {
    }
}
