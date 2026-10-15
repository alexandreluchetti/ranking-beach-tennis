package com.lucchetta.entrypoint.rest.me;

import com.jayway.jsonpath.JsonPath;
import com.lucchetta.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The caller's own statistics over every league (decision D-022). */
class MeStatisticsApiIT extends IntegrationTest {

    private static final RequestPostProcessor ANA = token("ana-uid", "Ana", null);
    private static final RequestPostProcessor BETO = token("beto-uid", "Beto", null);
    private static final RequestPostProcessor CAIO = token("caio-uid", "Caio", null);
    private static final RequestPostProcessor DANI = token("dani-uid", "Dani", null);

    @Test
    void sumsTheCallersMatchesOfEveryLeagueWithWinRate() throws Exception {
        String north = leagueWithEveryone("Liga Norte");
        String south = leagueWithEveryone("Liga Sul");
        register(north, 6, 4);
        register(north, 7, 6);
        register(south, 3, 6);

        mockMvc.perform(get("/api/me/statistics").with(ANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchesPlayed").value(3))
                .andExpect(jsonPath("$.wins").value(2))
                .andExpect(jsonPath("$.losses").value(1))
                .andExpect(jsonPath("$.gamesWon").value(16))
                .andExpect(jsonPath("$.gamesLost").value(16))
                .andExpect(jsonPath("$.gameDifference").value(0))
                .andExpect(jsonPath("$.winRate").value(67))
                .andExpect(jsonPath("$.points").doesNotExist());
        mockMvc.perform(get("/api/me/statistics").with(CAIO))
                .andExpect(jsonPath("$.wins").value(1))
                .andExpect(jsonPath("$.winRate").value(33));
    }

    @Test
    void withoutMatchesTheWinRateIsEmpty() throws Exception {
        mockMvc.perform(get("/api/me/statistics").with(ANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchesPlayed").value(0))
                .andExpect(jsonPath("$.winRate").isEmpty());
    }

    private String leagueWithEveryone(String name) throws Exception {
        String league = JsonPath.read(mockMvc.perform(post("/api/leagues").with(ANA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"visibility\": \"PUBLIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        for (RequestPostProcessor user : new RequestPostProcessor[]{BETO, CAIO, DANI}) {
            mockMvc.perform(post("/api/leagues/" + league + "/join").with(user)).andExpect(status().isOk());
        }
        return league;
    }

    /** Ana and Beto (team A) against Caio and Dani. */
    private void register(String league, int teamAGames, int teamBGames) throws Exception {
        mockMvc.perform(post("/api/leagues/" + league + "/matches").with(ANA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "%s", "playedAt": "%s", "format": "STANDARD", "teamAGames": %d,
                                 "teamBGames": %d, "teamA": [{"playerId": "%s"}, {"playerId": "%s"}],
                                 "teamB": [{"playerId": "%s"}, {"playerId": "%s"}], "confirmPossibleDuplicate": true}
                                """.formatted(UUID.randomUUID(),
                                ZonedDateTime.now(ZoneId.of("America/Sao_Paulo")).minusHours(1).toOffsetDateTime(),
                                teamAGames, teamBGames, id(ANA), id(BETO), id(CAIO), id(DANI))))
                .andExpect(status().isCreated());
    }

    private String id(RequestPostProcessor user) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/me").with(user)).andReturn().getResponse()
                .getContentAsString(), "$.id");
    }
}
