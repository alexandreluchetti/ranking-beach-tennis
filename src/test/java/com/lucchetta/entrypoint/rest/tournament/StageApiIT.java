package com.lucchetta.entrypoint.rest.tournament;

import com.jayway.jsonpath.JsonPath;
import com.lucchetta.IntegrationTest;
import com.lucchetta.core.domain.league.DefaultLeague;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StageApiIT extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private static final String STAGES = "/api/leagues/" + DefaultLeague.ID + "/stages";
    private static final RequestPostProcessor ANA = token("ana-uid", "Ana", null);

    private final List<String> memberIds = new ArrayList<>();

    @BeforeEach
    void dailyTournamentWithMembers() throws Exception {
        mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Torneio", "startDate": "%s", "endDate": "%s",
                                 "rankingType": "DAILY_TOURNAMENT"}
                                """.formatted(TODAY.minusDays(10), TODAY.plusDays(30))))
                .andExpect(status().isCreated());
        memberIds.add(join(ANA));
        for (String name : List.of("Beto", "Caio", "Dani", "Edu", "Fabi", "Gabi", "Hugo")) {
            memberIds.add(join(token(name.toLowerCase() + "-uid", name, null)));
        }
    }

    @Test
    void wholeStageFromTheDrawToTheChampion() throws Exception {
        String body = mockMvc.perform(post(STAGES).with(adminToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(stageJson(memberIds)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(TODAY.toString()))
                .andExpect(jsonPath("$.format").value("STANDARD"))
                .andExpect(jsonPath("$.players.length()").value(8))
                .andExpect(jsonPath("$.players[0].displayName").value("Ana"))
                .andExpect(jsonPath("$.players[7].displayName").value("Hugo"))
                .andExpect(jsonPath("$.pairs.length()").value(4))
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.fixtures.length()").value(6))
                .andExpect(jsonPath("$.fixtures[0].round").value("GROUP"))
                .andExpect(jsonPath("$.fixtures[0].groupRound").value(1))
                .andExpect(jsonPath("$.fixtures[5].groupRound").value(3))
                .andExpect(jsonPath("$.fixtures[0].score").isEmpty())
                .andExpect(jsonPath("$.knockoutDrawn").value(false))
                .andExpect(jsonPath("$.canAdminister").value(true))
                .andReturn().getResponse().getContentAsString();
        String stage = JsonPath.read(body, "$.id");

        mockMvc.perform(get(STAGES).with(ANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(stage))
                .andExpect(jsonPath("$[0].playerCount").value(8));
        for (int fixture = 1; fixture <= 6; fixture++) {
            score(ANA, stage, fixture, 6, 4).andExpect(status().isOk());
        }
        String drawn = mockMvc.perform(get(STAGES + "/" + stage).with(ANA))
                .andExpect(jsonPath("$.knockoutDrawn").value(true))
                .andExpect(jsonPath("$.fixtures.length()").value(7))
                .andExpect(jsonPath("$.fixtures[6].round").value("FINAL"))
                .andExpect(jsonPath("$.fixtures[6].groupRound").isEmpty())
                .andExpect(jsonPath("$.groups[0].standings[0].wins").value(3))
                .andExpect(jsonPath("$.canAdminister").value(false))
                .andExpect(jsonPath("$.canEnterScores").value(true))
                .andReturn().getResponse().getContentAsString();
        int champion = JsonPath.read(drawn, "$.fixtures[6].pairA");

        score(ANA, stage, 7, 7, 6, ", \"tiebreakTeamAPoints\": 7, \"tiebreakTeamBPoints\": 5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finished").value(true))
                .andExpect(jsonPath("$.fixtures[6].score.teamAGames").value(7))
                .andExpect(jsonPath("$.pairs[?(@.number == " + champion + ")].placement").value("CHAMPION"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from match where stage_id = ?", Integer.class,
                UUID.fromString(stage))).isEqualTo(7);
        String ranking = mockMvc.perform(get("/api/leagues/" + DefaultLeague.ID + "/seasons/current/ranking")
                        .with(ANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season.rankingType").value("DAILY_TOURNAMENT"))
                // The eight present and the system administrator, who joined when creating the season (D-023).
                .andExpect(jsonPath("$.entries.length()").value(9))
                .andExpect(jsonPath("$.entries[0].points").value(100))
                .andExpect(jsonPath("$.entries[0].titles").value(1))
                .andExpect(jsonPath("$.entries[0].finals").value(1))
                .andExpect(jsonPath("$.entries[0].stagesPlayed").value(1))
                .andExpect(jsonPath("$.entries[7].points").value(20))
                .andExpect(jsonPath("$.entries[8].points").value(0))
                .andExpect(jsonPath("$.entries[8].stagesPlayed").value(0))
                .andReturn().getResponse().getContentAsString();
        String leader = JsonPath.read(ranking, "$.entries[0].playerId");
        mockMvc.perform(get("/api/leagues/" + DefaultLeague.ID + "/players/" + leader + "/profile").with(ANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSeason.position").value(1))
                .andExpect(jsonPath("$.currentSeason.stats.points").value(100))
                .andExpect(jsonPath("$.currentSeason.tournament.titles").value(1));
        mockMvc.perform(get("/api/leagues/" + DefaultLeague.ID + "/matches").with(ANA))
                .andExpect(jsonPath("$.items.length()").value(7))
                .andExpect(jsonPath("$.items[0].stageId").value(stage))
                .andExpect(jsonPath("$.items[0].counted").value(true))
                .andExpect(jsonPath("$.items[0].teamA[0].points").value(0));
        String matchId = JsonPath.read(drawn, "$.fixtures[0].score.matchId");
        mockMvc.perform(post("/api/leagues/" + DefaultLeague.ID + "/matches/" + matchId + "/void").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"teste\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STAGE_MATCH"));
        mockMvc.perform(delete(STAGES + "/" + stage).with(adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STAGE_STARTED"));
    }

    @Test
    void scoresAreCorrectedAndClearedByAdministrators() throws Exception {
        String stage = createStage();
        score(ANA, stage, 1, 6, 4).andExpect(status().isOk());

        score(ANA, stage, 1, 6, 2).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAGE_SCORE_ALREADY_ENTERED"))
                .andExpect(jsonPath("$.detail")
                        .value("Este jogo já tem placar. Só os administradores podem corrigi-lo."));
        score(adminToken(), stage, 1, 6, 2, ", \"expectedMatchVersion\": 0, \"reason\": \"Anotado errado\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixtures[0].score.teamBGames").value(2))
                .andExpect(jsonPath("$.fixtures[0].score.matchVersion").value(1));
        score(adminToken(), stage, 1, 6, 1, ", \"expectedMatchVersion\": 0")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_MODIFIED"));
        score(adminToken(), stage, 2, 6, 6).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCORE"));

        mockMvc.perform(post(STAGES + "/" + stage + "/fixtures/1/clear").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Dupla trocada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixtures[0].score").isEmpty());
        assertThat(jdbcTemplate.queryForList("select action from match_audit order by id", String.class))
                .containsExactly("CORRECTED", "VOIDED");
    }

    @Test
    void stageRulesAreExplainedInPortuguese() throws Exception {
        mockMvc.perform(post(STAGES).with(ANA).contentType(MediaType.APPLICATION_JSON)
                        .content(stageJson(memberIds, "\"Convidado 1\"", "\"Convidado 2\"")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(STAGES).with(adminToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(stageJson(memberIds, "\"Convidado 1\"", "\"Convidado 2\"", "\"Convidado 3\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STAGE_ODD_PLAYER_COUNT"))
                .andExpect(jsonPath("$.detail")
                        .value("O número de jogadores precisa ser par: inclua um convidado ou retire alguém."));
        mockMvc.perform(post(STAGES).with(adminToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(stageJson(memberIds.subList(0, 4), "\"Convidado 1\"", "\"Convidado 2\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STAGE_PLAYER_COUNT"));
        mockMvc.perform(get(STAGES + "/" + UUID.randomUUID()).with(ANA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAGE_NOT_FOUND"));

        String stage = createStage();
        mockMvc.perform(post(STAGES).with(adminToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(stageJson(memberIds, "\"Convidado 1\"", "\"Convidado 2\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_ALREADY_EXISTS"));
        mockMvc.perform(get(STAGES + "/" + stage).with(token("zeca-uid", "Zeca", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void drawIsRedoneAndTheStageDeletedBeforeTheFirstScore() throws Exception {
        String stage = createStage();

        mockMvc.perform(post(STAGES + "/" + stage + "/draw").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"format\": \"SHORT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("SHORT"))
                .andExpect(jsonPath("$.players.length()").value(10));
        mockMvc.perform(delete(STAGES + "/" + stage).with(adminToken()))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select count(*) from tournament_stage", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from tournament_fixture", Integer.class)).isZero();
    }

    private String createStage() throws Exception {
        String body = mockMvc.perform(post(STAGES).with(adminToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(stageJson(memberIds, "\"Convidado 1\"", "\"Convidado 2\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions score(RequestPostProcessor user, String stage,
                                                                     int fixture, int a, int b) throws Exception {
        return score(user, stage, fixture, a, b, "");
    }

    private org.springframework.test.web.servlet.ResultActions score(RequestPostProcessor user, String stage,
                                                                     int fixture, int a, int b, String extra)
            throws Exception {
        return mockMvc.perform(put(STAGES + "/" + stage + "/fixtures/" + fixture + "/score").with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamAGames\": %d, \"teamBGames\": %d%s}".formatted(a, b, extra)));
    }

    private static String stageJson(List<String> members, String... guests) {
        String players = members.stream().map(id -> "{\"playerId\": \"" + id + "\"}")
                .collect(Collectors.joining(", "));
        for (String guest : guests) {
            players += ", {\"guestName\": " + guest + "}";
        }
        return "{\"format\": \"STANDARD\", \"players\": [" + players + "]}";
    }

    private String join(RequestPostProcessor user) throws Exception {
        mockMvc.perform(post("/api/leagues/" + DefaultLeague.ID + "/join").with(user)).andExpect(status().isOk());
        String body = mockMvc.perform(get("/api/me").with(user)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
