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
    void playingDaysAreChosenAtCreationAndCanChange() throws Exception {
        String planned = createSeason(
                json("Verão", TODAY.plusDays(10), TODAY.plusDays(90), "\"SATURDAY\", \"TUESDAY\""));

        mockMvc.perform(get("/api/seasons/" + planned).with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playingDays.length()").value(2))
                .andExpect(jsonPath("$.playingDays[0]").value("TUESDAY"))
                .andExpect(jsonPath("$.playingDays[1]").value("SATURDAY"));
        mockMvc.perform(patch("/api/admin/seasons/" + planned).with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playingDays\": [\"SUNDAY\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playingDays[0]").value("SUNDAY"));
        assertThat(jdbcTemplate.queryForObject("select playing_days::text from season where id = ?", String.class,
                UUID.fromString(planned))).isEqualTo("{7}");
        assertThat(jdbcTemplate.queryForObject("""
                select after::text from season_audit where season_id = ? and action = 'UPDATED'
                """, String.class, UUID.fromString(planned))).contains("\"playingDays\": [\"SUNDAY\"]");

        String active = createSeason("Primavera", TODAY.minusDays(5), TODAY.plusDays(5));
        mockMvc.perform(get("/api/seasons/" + active).with(userToken()))
                .andExpect(jsonPath("$.playingDays.length()").value(7))
                .andExpect(jsonPath("$.playingDays[0]").value("MONDAY"));
        mockMvc.perform(patch("/api/admin/seasons/" + active).with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playingDays\": [\"MONDAY\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playingDays.length()").value(1))
                .andExpect(jsonPath("$.playingDays[0]").value("MONDAY"));
        // Active season: the earlier days keep every day of the week (D-025).
        assertThat(jdbcTemplate.queryForMap(
                "select last_day, playing_days::text as days from season_past_playing_days where season_id = ?",
                UUID.fromString(active))).containsEntry("last_day", java.sql.Date.valueOf(TODAY.minusDays(1)))
                .containsEntry("days", "{1,2,3,4,5,6,7}");
        assertThat(jdbcTemplate.queryForObject("select count(*) from season_past_playing_days where season_id = ?",
                Integer.class, UUID.fromString(planned))).isZero();
    }

    @Test
    void invalidPlayingDays() throws Exception {
        mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Primavera", TODAY, TODAY.plusDays(30), "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEASON_PLAYING_DAYS"))
                .andExpect(jsonPath("$.detail").value("Escolha pelo menos um dia da semana para a temporada."));
        mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Primavera", TODAY, TODAY.plusDays(30), "\"SEGUNDA\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from season", Integer.class)).isZero();
    }

    @Test
    void rankingTypeIsChosenAtCreationAndLockedOnceTheSeasonStarts() throws Exception {
        String regular = createSeason("Primavera", TODAY.minusDays(5), TODAY.plusDays(5));
        mockMvc.perform(get("/api/seasons/" + regular).with(userToken()))
                .andExpect(jsonPath("$.rankingType").value("MATCH_POINTS"))
                .andExpect(jsonPath("$.placementPoints").doesNotExist());
        mockMvc.perform(patch("/api/admin/seasons/" + regular).with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rankingType\": \"DAILY_TOURNAMENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEASON_RANKING_LOCKED"))
                .andExpect(jsonPath("$.detail")
                        .value("O tipo de ranking e os pontos só podem mudar antes de a temporada começar."));

        String tournament = createSeason("""
                {"name": "Torneio", "startDate": "%s", "endDate": "%s", "rankingType": "DAILY_TOURNAMENT"}
                """.formatted(TODAY.plusDays(10), TODAY.plusDays(90)));
        mockMvc.perform(get("/api/seasons/" + tournament).with(userToken()))
                .andExpect(jsonPath("$.rankingType").value("DAILY_TOURNAMENT"))
                .andExpect(jsonPath("$.placementPoints.champion").value(100))
                .andExpect(jsonPath("$.placementPoints.groupStage").value(20));
        mockMvc.perform(patch("/api/admin/seasons/" + tournament).with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placementPoints": {"champion": 60, "runnerUp": 40, "thirdPlace": 30,
                                 "fourthPlace": 25, "quarterfinal": 20, "roundOf16": 15, "groupStage": 10}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placementPoints.champion").value(60))
                .andExpect(jsonPath("$.placementPoints.quarterfinal").value(20))
                .andExpect(jsonPath("$.placementPoints.roundOf16").value(15));
        assertThat(jdbcTemplate.queryForMap(
                "select ranking_type, placement_points::text as points from season where id = ?",
                UUID.fromString(tournament)))
                .containsEntry("ranking_type", "DAILY_TOURNAMENT")
                .containsEntry("points", "{60,40,30,25,20,15,10}");
        assertThat(jdbcTemplate.queryForObject("""
                select after::text from season_audit where season_id = ? and action = 'UPDATED'
                """, String.class, UUID.fromString(tournament))).contains("\"champion\": 60");
    }

    @Test
    void invalidPlacementPoints() throws Exception {
        mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Torneio", "startDate": "%s", "endDate": "%s",
                                 "rankingType": "DAILY_TOURNAMENT", "placementPoints": {"champion": 10,
                                 "runnerUp": 20, "thirdPlace": 5, "fourthPlace": 4, "quarterfinal": 3, "roundOf16": 2,
                                 "groupStage": 1}}
                                """.formatted(TODAY, TODAY.plusDays(30))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLACEMENT_POINTS"));
        mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Primavera", "startDate": "%s", "endDate": "%s", "placementPoints":
                                 {"champion": 100, "runnerUp": 70, "thirdPlace": 55, "fourthPlace": 45,
                                  "quarterfinal": 35, "roundOf16": 25, "groupStage": 20}}
                                """.formatted(TODAY, TODAY.plusDays(30))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACEMENT_POINTS_NOT_APPLICABLE"));
        mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Torneio", "startDate": "%s", "endDate": "%s",
                                 "rankingType": "DAILY_TOURNAMENT", "placementPoints": {"champion": 100}}
                                """.formatted(TODAY, TODAY.plusDays(30))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from season", Integer.class)).isZero();
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
        return createSeason(json(name, start, end));
    }

    private String createSeason(String json) throws Exception {
        String body = mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String json(String name, LocalDate start, LocalDate end) {
        return "{\"name\": \"%s\", \"startDate\": \"%s\", \"endDate\": \"%s\"}".formatted(name, start, end);
    }

    private static String json(String name, LocalDate start, LocalDate end, String playingDays) {
        return "{\"name\": \"%s\", \"startDate\": \"%s\", \"endDate\": \"%s\", \"playingDays\": [%s]}"
                .formatted(name, start, end, playingDays);
    }
}
