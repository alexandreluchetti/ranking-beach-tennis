package com.lucchetta.entrypoint.rest.league;

import com.jayway.jsonpath.JsonPath;
import com.lucchetta.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Decision D-031: matches whose four players are members of two leagues count in both. */
class SharedMatchesApiIT extends IntegrationTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.now(BRASILIA);

    private static final RequestPostProcessor ANA = token("ana-uid", "Ana", null);
    private static final RequestPostProcessor BETO = token("beto-uid", "Beto", null);
    private static final RequestPostProcessor CAIO = token("caio-uid", "Caio", null);
    private static final RequestPostProcessor DANI = token("dani-uid", "Dani", null);
    private static final RequestPostProcessor EVA = token("eva-uid", "Eva", null);

    private String north;
    private String south;
    private String ana;
    private String beto;
    private String caio;
    private String dani;

    @BeforeEach
    void twoLeaguesWithTheSamePlayers() throws Exception {
        north = createLeague(ANA, "Liga Norte");
        south = createLeague(EVA, "Liga Sul");
        for (RequestPostProcessor player : new RequestPostProcessor[]{BETO, CAIO, DANI}) {
            mockMvc.perform(post("/api/leagues/" + north + "/join").with(player)).andExpect(status().isOk());
        }
        for (RequestPostProcessor player : new RequestPostProcessor[]{ANA, BETO, CAIO}) {
            mockMvc.perform(post("/api/leagues/" + south + "/join").with(player)).andExpect(status().isOk());
        }
        createSeason(ANA, north);
        createSeason(EVA, south);
        ana = playerId(ANA);
        beto = playerId(BETO);
        caio = playerId(CAIO);
        dani = playerId(DANI);
    }

    @Test
    void matchCountsInBothLeaguesOnceAllFourPlayersAreMembers() throws Exception {
        String match = JsonPath.read(register(ANA, north).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        // Dani is not a member of the south league yet.
        mockMvc.perform(get("/api/leagues/" + south + "/matches").with(EVA))
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/leagues/" + south + "/matches/" + match).with(EVA))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/leagues/" + south + "/join").with(DANI)).andExpect(status().isOk());

        mockMvc.perform(get("/api/leagues/" + south + "/matches").with(EVA))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(match))
                .andExpect(jsonPath("$.items[0].leagueId").value(north))
                .andExpect(jsonPath("$.items[0].counted").value(true))
                .andExpect(jsonPath("$.items[0].season.name").value("Primavera Sul"));
        mockMvc.perform(get("/api/leagues/" + south + "/seasons/current/ranking").with(EVA))
                .andExpect(jsonPath("$.entries[0].playerId").value(ana))
                .andExpect(jsonPath("$.entries[0].points").value(9));
        mockMvc.perform(get("/api/leagues/" + north + "/seasons/current/ranking").with(ANA))
                .andExpect(jsonPath("$.entries[0].points").value(9));
        mockMvc.perform(get("/api/leagues/" + south + "/players/" + caio + "/profile").with(EVA))
                .andExpect(jsonPath("$.currentSeason.stats.matchesPlayed").value(1));
        // Global statistics count the match once (D-022).
        mockMvc.perform(get("/api/me/statistics").with(ANA))
                .andExpect(jsonPath("$.matchesPlayed").value(1));

        // Only the league where it was registered changes it, and the other league follows.
        mockMvc.perform(post("/api/leagues/" + south + "/matches/" + match + "/void").with(EVA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"teste\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/leagues/" + north + "/matches/" + match + "/void").with(ANA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Lançada errada\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/leagues/" + south + "/seasons/current/ranking").with(EVA))
                .andExpect(jsonPath("$.entries[0].points").value(0));
        mockMvc.perform(get("/api/leagues/" + south + "/matches/" + match).with(EVA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));
    }

    @Test
    void leagueMayStopReceivingSharedMatches() throws Exception {
        mockMvc.perform(post("/api/leagues/" + south + "/join").with(DANI)).andExpect(status().isOk());
        register(ANA, north).andExpect(status().isCreated());
        mockMvc.perform(get("/api/leagues/" + south + "/seasons/current/ranking").with(EVA))
                .andExpect(jsonPath("$.entries[0].points").value(9));

        mockMvc.perform(patch("/api/leagues/" + south).with(EVA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sharedMatches\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharedMatches").value(false));

        mockMvc.perform(get("/api/leagues/" + south + "/seasons/current/ranking").with(EVA))
                .andExpect(jsonPath("$.entries[0].points").value(0));
        mockMvc.perform(get("/api/leagues/" + south + "/matches").with(EVA))
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/leagues/" + north).with(ANA))
                .andExpect(jsonPath("$.sharedMatches").value(true));
    }

    @Test
    void sameGameInTheOtherLeagueIsAPossibleDuplicate() throws Exception {
        mockMvc.perform(post("/api/leagues/" + south + "/join").with(DANI)).andExpect(status().isOk());
        String match = JsonPath.read(register(ANA, north).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        register(BETO, south)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POSSIBLE_DUPLICATE_MATCH"))
                .andExpect(jsonPath("$.existingMatchIds[0]").value(match));
    }

    private String createLeague(RequestPostProcessor user, String name) throws Exception {
        String body = mockMvc.perform(post("/api/leagues").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"visibility\": \"PUBLIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void createSeason(RequestPostProcessor admin, String leagueId) throws Exception {
        String name = leagueId.equals(north) ? "Primavera Norte" : "Primavera Sul";
        mockMvc.perform(post("/api/leagues/" + leagueId + "/seasons").with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"startDate\": \"%s\", \"endDate\": \"%s\"}"
                                .formatted(name, TODAY.minusDays(10), TODAY.plusDays(30))))
                .andExpect(status().isCreated());
    }

    private String playerId(RequestPostProcessor user) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/me").with(user)).andReturn().getResponse()
                .getContentAsString(), "$.id");
    }

    /** Ana + Beto beat Caio + Dani 6-4. */
    private ResultActions register(RequestPostProcessor user, String leagueId) throws Exception {
        return mockMvc.perform(post("/api/leagues/" + leagueId + "/matches").with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id": "%s", "playedAt": "%s", "format": "STANDARD", "teamAGames": 6, "teamBGames": 4,
                         "teamA": [{"playerId": "%s"}, {"playerId": "%s"}],
                         "teamB": [{"playerId": "%s"}, {"playerId": "%s"}]}
                        """.formatted(UUID.randomUUID(), ZonedDateTime.now(BRASILIA).minusHours(1).toOffsetDateTime(),
                        ana, beto, caio, dani)));
    }
}
