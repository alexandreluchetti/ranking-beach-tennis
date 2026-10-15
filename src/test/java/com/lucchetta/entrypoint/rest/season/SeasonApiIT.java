package com.lucchetta.entrypoint.rest.season;

import com.lucchetta.IntegrationTest;
import com.lucchetta.core.domain.league.DefaultLeague;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeasonApiIT extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Test
    void adminCreatesSeasonVisibleToEveryUser() throws Exception {
        String id = createSeason("Primavera", TODAY.minusDays(5), TODAY.plusDays(60));

        mockMvc.perform(get("/api/seasons/current").with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Primavera"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/api/seasons").with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/seasons/" + id).with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value(TODAY.plusDays(60).toString()));
    }

    @Test
    void creationIsAudited() throws Exception {
        String id = createSeason("Primavera", TODAY, TODAY.plusDays(60));

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "select action, before, after::text as after from season_audit where season_id = ?", UUID.fromString(id));
        assertThat(audit.get("action")).isEqualTo("CREATED");
        assertThat(audit.get("before")).isNull();
        assertThat((String) audit.get("after")).contains("\"name\": \"Primavera\"");
    }

    @Test
    void noActiveSeasonReturnsNoContent() throws Exception {
        createSeason("Verão", TODAY.plusDays(10), TODAY.plusDays(90));

        mockMvc.perform(get("/api/seasons/current").with(userToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void regularUserCannotCreateOrChangeSeasons() throws Exception {
        mockMvc.perform(post("/api/admin/seasons").with(userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Primavera", TODAY, TODAY.plusDays(60))))
                .andExpect(status().isForbidden());

        String id = createSeason("Primavera", TODAY, TODAY.plusDays(60));
        mockMvc.perform(patch("/api/admin/seasons/" + id).with(userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\": \"" + TODAY.plusDays(90) + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void overlappingSeasonIsRejectedWithPortugueseMessage() throws Exception {
        createSeason("Primavera", TODAY, TODAY.plusDays(60));

        mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Verão", TODAY.plusDays(60), TODAY.plusDays(120))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_OVERLAP"))
                .andExpect(jsonPath("$.detail").value("O período informado se sobrepõe ao de outra temporada."));
    }

    @Test
    void databaseRejectsOverlapEvenWhenBypassingTheApplication() throws Exception {
        String id = createSeason("Primavera", TODAY, TODAY.plusDays(60));
        UUID createdBy = jdbcTemplate.queryForObject("select created_by from season where id = ?", UUID.class,
                UUID.fromString(id));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into season (id, league_id, name, start_date, end_date, created_by, created_at, updated_at)
                values (?, ?, 'Paralela', ?, ?, ?, now(), now())
                """, UUID.randomUUID(), DefaultLeague.ID, TODAY.plusDays(60), TODAY.plusDays(90), createdBy))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_season_no_overlap");
    }

    @Test
    void adminExtendsActiveSeasonAndChangeIsAudited() throws Exception {
        String id = createSeason("Primavera", TODAY.minusDays(5), TODAY.plusDays(60));

        mockMvc.perform(patch("/api/admin/seasons/" + id).with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\": \"" + TODAY.plusDays(90) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value(TODAY.plusDays(90).toString()))
                .andExpect(jsonPath("$.startDate").value(TODAY.minusDays(5).toString()));

        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                select before::text as before, after::text as after from season_audit
                where season_id = ? and action = 'UPDATED'
                """, UUID.fromString(id));
        assertThat((String) audit.get("before")).contains(TODAY.plusDays(60).toString());
        assertThat((String) audit.get("after")).contains(TODAY.plusDays(90).toString());
    }

    @Test
    void activeSeasonStartDateCannotChange() throws Exception {
        String id = createSeason("Primavera", TODAY.minusDays(5), TODAY.plusDays(60));

        mockMvc.perform(patch("/api/admin/seasons/" + id).with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\": \"" + TODAY.minusDays(10) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEASON_START_LOCKED"));
    }

    @Test
    void invalidInputAndUnknownSeason() throws Exception {
        mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Primavera\", \"startDate\": \"31/12/2026\", \"endDate\": \"2027-01-31\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/seasons/" + UUID.randomUUID()).with(userToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASON_NOT_FOUND"));
    }

    private String createSeason(String name, LocalDate start, LocalDate end) throws Exception {
        String body = mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(name, start, end)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String json(String name, LocalDate start, LocalDate end) {
        return "{\"name\": \"%s\", \"startDate\": \"%s\", \"endDate\": \"%s\"}".formatted(name, start, end);
    }
}
