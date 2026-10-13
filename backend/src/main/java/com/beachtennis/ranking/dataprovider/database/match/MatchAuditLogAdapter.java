package com.beachtennis.ranking.dataprovider.database.match;

import com.beachtennis.ranking.core.domain.match.Match;
import com.beachtennis.ranking.core.domain.match.MatchAuditEntry;
import com.beachtennis.ranking.core.domain.match.Participant;
import com.beachtennis.ranking.core.port.MatchAuditLog;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MatchAuditLogAdapter implements MatchAuditLog {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public MatchAuditLogAdapter(JdbcClient jdbcClient, JsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
    }

    @Override
    @Transactional
    public void record(MatchAuditEntry entry) {
        jdbcClient.sql("""
                        insert into match_audit (match_id, action, performed_by, performed_at, reason, before, after)
                        values (:matchId, :action, :performedBy, :performedAt, :reason,
                                cast(:before as jsonb), cast(:after as jsonb))
                        """)
                .param("matchId", entry.matchId())
                .param("action", entry.action().name())
                .param("performedBy", entry.performedBy())
                .param("performedAt", OffsetDateTime.ofInstant(entry.performedAt(), ZoneOffset.UTC))
                .param("reason", entry.reason())
                .param("before", jsonMapper.writeValueAsString(snapshot(entry.before())))
                .param("after", jsonMapper.writeValueAsString(snapshot(entry.after())))
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Record> findByMatch(UUID matchId) {
        return jdbcClient.sql("""
                        select action, performed_by, performed_at, reason, before::text as before, after::text as after
                        from match_audit where match_id = :matchId order by id
                        """)
                .param("matchId", matchId)
                .query((rs, rowNum) -> new Record(
                        MatchAuditEntry.Action.valueOf(rs.getString("action")),
                        rs.getObject("performed_by", UUID.class),
                        rs.getObject("performed_at", OffsetDateTime.class).toInstant(),
                        rs.getString("reason"),
                        jsonMapper.readValue(rs.getString("before"), MAP),
                        jsonMapper.readValue(rs.getString("after"), MAP)))
                .list();
    }

    /** Self-contained copy of what an administrator can change. */
    private static Map<String, Object> snapshot(Match match) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("playedAt", match.playedAt().toString());
        snapshot.put("format", match.format().name());
        snapshot.put("teamAGames", match.score().teamAGames());
        snapshot.put("teamBGames", match.score().teamBGames());
        snapshot.put("tiebreakTeamAPoints", match.score().tiebreakTeamAPoints());
        snapshot.put("tiebreakTeamBPoints", match.score().tiebreakTeamBPoints());
        snapshot.put("status", match.status().name());
        snapshot.put("participants", match.participants().stream().map(MatchAuditLogAdapter::participant).toList());
        return snapshot;
    }

    private static Map<String, Object> participant(Participant p) {
        Map<String, Object> value = new HashMap<>();
        value.put("team", p.team().name());
        value.put("slot", p.slot());
        value.put("playerId", p.playerId() == null ? null : p.playerId().toString());
        value.put("guestName", p.guestName());
        return value;
    }
}
