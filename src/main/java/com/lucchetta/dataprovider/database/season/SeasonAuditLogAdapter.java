package com.lucchetta.dataprovider.database.season;

import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.SeasonAuditEntry;
import com.lucchetta.core.port.SeasonAuditLog;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SeasonAuditLogAdapter implements SeasonAuditLog {

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public SeasonAuditLogAdapter(JdbcClient jdbcClient, JsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
    }

    @Override
    @Transactional
    public void record(SeasonAuditEntry entry) {
        jdbcClient.sql("""
                        insert into season_audit (season_id, action, performed_by, performed_at, before, after)
                        values (:seasonId, :action, :performedBy, :performedAt, cast(:before as jsonb), cast(:after as jsonb))
                        """)
                .param("seasonId", entry.seasonId())
                .param("action", entry.action().name())
                .param("performedBy", entry.performedBy())
                .param("performedAt", OffsetDateTime.ofInstant(entry.performedAt(), ZoneOffset.UTC))
                .param("before", toJson(entry.before()))
                .param("after", toJson(entry.after()))
                .update();
    }

    private String toJson(Season season) {
        if (season == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", season.name());
        snapshot.put("startDate", season.startDate().toString());
        snapshot.put("endDate", season.endDate().toString());
        return jsonMapper.writeValueAsString(snapshot);
    }
}
