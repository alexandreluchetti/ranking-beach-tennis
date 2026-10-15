package com.lucchetta.entrypoint.rest.match;

import com.lucchetta.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminMatchApiIT extends IntegrationTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private String ana;
    private String beto;
    private String caio;
    private String dani;
    private String playedAt;
    private String matchId;

    @BeforeEach
    void registerMatch() throws Exception {
        ana = playerId("uid-ana", "Ana");
        beto = playerId("uid-beto", "Beto");
        caio = playerId("uid-caio", "Caio");
        dani = playerId("uid-dani", "Dani");
        LocalDate today = LocalDate.now(BRASILIA);
        mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Primavera\", \"startDate\": \"%s\", \"endDate\": \"%s\"}"
                                .formatted(today.minusDays(30), today.plusDays(30))))
                .andExpect(status().isCreated());

        matchId = UUID.randomUUID().toString();
        playedAt = ZonedDateTime.now(BRASILIA).minusHours(1).toOffsetDateTime().toString();
        mockMvc.perform(post("/api/matches").with(token("uid-ana", "Ana", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "%s", "playedAt": "%s", "format": "STANDARD", "teamAGames": 6, "teamBGames": 4,
                                 "teamA": [{"playerId": "%s"}, {"playerId": "%s"}],
                                 "teamB": [{"playerId": "%s"}, {"playerId": "%s"}]}
                                """.formatted(matchId, playedAt, ana, beto, caio, dani)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void correctionUpdatesMatchRankingAndAudit() throws Exception {
        correct(0, 6, 2, "Placar anotado errado")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamBGames").value(2))
                .andExpect(jsonPath("$.teamB[0].points").value(2))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/seasons/current/ranking").with(userToken()))
                .andExpect(jsonPath("$.entries[?(@.displayName == 'Caio')].points").value(2));
        mockMvc.perform(get("/api/matches/" + matchId + "/audit").with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("CORRECTED"))
                .andExpect(jsonPath("$[0].reason").value("Placar anotado errado"))
                .andExpect(jsonPath("$[0].before.teamBGames").value(4))
                .andExpect(jsonPath("$[0].after.teamBGames").value(2))
                .andExpect(jsonPath("$[0].before.participants.length()").value(4));
    }

    @Test
    void staleCorrectionIsRejected() throws Exception {
        correct(0, 6, 2, null).andExpect(status().isOk());

        correct(0, 6, 3, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_MODIFIED"));
    }

    @Test
    void voidAndRevertAreReflectedInRankingAndHistory() throws Exception {
        mockMvc.perform(post("/api/admin/matches/" + matchId + "/void").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));

        mockMvc.perform(post("/api/admin/matches/" + matchId + "/void").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Jogo de treino\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.counted").value(false))
                .andExpect(jsonPath("$.notCountedReason").value("VOIDED"))
                .andExpect(jsonPath("$.voiding.reason").value("Jogo de treino"));

        mockMvc.perform(get("/api/seasons/current/ranking").with(userToken()))
                .andExpect(jsonPath("$.entries[?(@.displayName == 'Ana')].points").value(0))
                .andExpect(jsonPath("$.entries[?(@.displayName == 'Ana')].matchesPlayed").value(0));
        mockMvc.perform(get("/api/matches").param("outcome", "VOIDED").with(userToken()))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(post("/api/admin/matches/" + matchId + "/revert-void").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Anulada por engano\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.counted").value(true))
                .andExpect(jsonPath("$.voiding").doesNotExist());

        mockMvc.perform(get("/api/seasons/current/ranking").with(userToken()))
                .andExpect(jsonPath("$.entries[?(@.displayName == 'Ana')].points").value(9));
        mockMvc.perform(get("/api/matches/" + matchId + "/audit").with(userToken()))
                .andExpect(jsonPath("$[0].action").value("VOIDED"))
                .andExpect(jsonPath("$[1].action").value("VOID_REVERTED"))
                .andExpect(jsonPath("$[1].reason").value("Anulada por engano"));

        Integer audits = jdbcTemplate.queryForObject("select count(*) from match_audit", Integer.class);
        assertThat(audits).isEqualTo(2);
    }

    @Test
    void regularUsersCannotAdministerMatches() throws Exception {
        mockMvc.perform(post("/api/admin/matches/" + matchId + "/void").with(userToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/matches/" + matchId).with(token("uid-ana", "Ana", null))
                        .contentType(MediaType.APPLICATION_JSON).content(correctionBody(0, 6, 0, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/matches/" + matchId).with(userToken()))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.teamBGames").value(4));
    }

    private ResultActions correct(int version, int teamAGames, int teamBGames, String reason) throws Exception {
        return mockMvc.perform(put("/api/admin/matches/" + matchId).with(adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctionBody(version, teamAGames, teamBGames, reason)));
    }

    private String correctionBody(int version, int teamAGames, int teamBGames, String reason) {
        return """
                {"version": %d, "playedAt": "%s", "format": "STANDARD", "teamAGames": %d, "teamBGames": %d,
                 "teamA": [{"playerId": "%s"}, {"playerId": "%s"}],
                 "teamB": [{"playerId": "%s"}, {"playerId": "%s"}],
                 "reason": %s}
                """.formatted(version, playedAt, teamAGames, teamBGames, ana, beto, caio, dani,
                reason == null ? "null" : "\"" + reason + "\"");
    }

    /** Signs in (app before leagues): any endpoint without a league joins the "Beach Tennis Arena". */
    private String playerId(String uid, String name) throws Exception {
        mockMvc.perform(get("/api/seasons").with(token(uid, name, null))).andExpect(status().isOk());
        return JsonPath.read(mockMvc.perform(get("/api/me").with(token(uid, name, null)))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }
}
