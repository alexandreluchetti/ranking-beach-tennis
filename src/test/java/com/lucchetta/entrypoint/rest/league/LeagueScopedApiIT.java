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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Seasons, matches, rankings and players inside a league (decisions D-020 and D-021). */
class LeagueScopedApiIT extends IntegrationTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.now(BRASILIA);

    private static final RequestPostProcessor ANA = token("ana-uid", "Ana", null);
    private static final RequestPostProcessor BETO = token("beto-uid", "Beto", null);
    private static final RequestPostProcessor CAIO = token("caio-uid", "Caio", null);
    private static final RequestPostProcessor DANI = token("dani-uid", "Dani", null);
    private static final RequestPostProcessor EVA = token("eva-uid", "Eva", null);

    private String league;
    private String other;
    private String ana;
    private String beto;
    private String caio;
    private String dani;
    private String eva;

    @BeforeEach
    void createLeagues() throws Exception {
        league = createLeague(ANA, "Liga Norte");
        other = createLeague(EVA, "Liga Sul");
        for (RequestPostProcessor member : new RequestPostProcessor[]{BETO, CAIO, DANI}) {
            mockMvc.perform(post("/api/leagues/" + league + "/join").with(member)).andExpect(status().isOk());
        }
        ana = playerId(ANA);
        beto = playerId(BETO);
        caio = playerId(CAIO);
        dani = playerId(DANI);
        eva = playerId(EVA);
    }

    @Test
    void leagueAdministratorManagesSeasonsAndEachLeagueHasItsOwn() throws Exception {
        createSeason(ANA, league).andExpect(status().isCreated());
        createSeason(EVA, other).andExpect(status().isCreated()); // same period, another league
        createSeason(BETO, league).andExpect(status().isForbidden());
        createSeason(ANA, league)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_OVERLAP"));

        mockMvc.perform(get("/api/leagues/" + league + "/seasons/current").with(BETO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Primavera"));
        mockMvc.perform(get("/api/leagues/" + league + "/seasons").with(EVA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/seasons/current").with(BETO))
                .andExpect(status().isNoContent()); // the Arena league has no season
    }

    @Test
    void matchesNeedLeagueMembersAndRankTheLeagueOnly() throws Exception {
        createSeason(ANA, league).andExpect(status().isCreated());
        createSeason(EVA, other).andExpect(status().isCreated());

        register(BETO, league, ana, beto, caio, dani).andExpect(status().isCreated());
        register(BETO, league, ana, beto, caio, eva)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_LEAGUE_MEMBER"));
        register(EVA, league, ana, beto, caio, dani).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/leagues/" + league + "/seasons/current/ranking").with(CAIO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(4))
                .andExpect(jsonPath("$.entries[0].points").value(9));
        mockMvc.perform(get("/api/leagues/" + other + "/seasons/current/ranking").with(EVA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].displayName").value("Eva"))
                .andExpect(jsonPath("$.entries[0].points").value(0));
        mockMvc.perform(get("/api/leagues/" + league + "/players").with(DANI))
                .andExpect(jsonPath("$.length()").value(4));
        mockMvc.perform(get("/api/leagues/" + league + "/players/" + ana + "/profile").with(DANI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.career.matchesPlayed").value(1));
        mockMvc.perform(get("/api/leagues/" + other + "/players/" + ana + "/profile").with(EVA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }

    @Test
    void guestMatchesGivePointsToTheRegisteredPlayers() throws Exception {
        createSeason(ANA, league).andExpect(status().isCreated());

        register(BETO, league, ana, beto, caio, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.counted").value(true))
                .andExpect(jsonPath("$.teamA[0].points").value(9))
                .andExpect(jsonPath("$.teamB[1].points").value(0));
    }

    @Test
    void leagueAdministratorAdministersMatchesAndIsNotified() throws Exception {
        String matchId = JsonPath.read(register(BETO, league, beto, caio, dani, null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/notifications").with(ANA))
                .andExpect(jsonPath("$.items[0].type").value("MATCH_REGISTERED"))
                .andExpect(jsonPath("$.items[0].leagueId").value(league))
                .andExpect(jsonPath("$.items[0].matchId").value(matchId));
        mockMvc.perform(post("/api/leagues/" + league + "/matches/" + matchId + "/void").with(BETO)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"treino\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/leagues/" + league + "/matches/" + matchId + "/void").with(ANA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"treino\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));
        mockMvc.perform(get("/api/leagues/" + other + "/matches/" + matchId).with(EVA))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/leagues/" + league + "/matches").with(DANI))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    private String createLeague(RequestPostProcessor user, String name) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/leagues").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"visibility\": \"PUBLIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String playerId(RequestPostProcessor user) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/me").with(user)).andReturn().getResponse()
                .getContentAsString(), "$.id");
    }

    private ResultActions createSeason(RequestPostProcessor user, String leagueId) throws Exception {
        return mockMvc.perform(post("/api/leagues/" + leagueId + "/seasons").with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Primavera\", \"startDate\": \"%s\", \"endDate\": \"%s\"}"
                        .formatted(TODAY.minusDays(10), TODAY.plusDays(30))));
    }

    /** Team A: a + b, team B: c + d (d null for a guest). */
    private ResultActions register(RequestPostProcessor user, String leagueId, String a, String b, String c, String d)
            throws Exception {
        String last = d == null ? "{\"guest\": true, \"guestName\": \"Zé\"}" : "{\"playerId\": \"" + d + "\"}";
        return mockMvc.perform(post("/api/leagues/" + leagueId + "/matches").with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id": "%s", "playedAt": "%s", "format": "STANDARD", "teamAGames": 6, "teamBGames": 4,
                         "teamA": [{"playerId": "%s"}, {"playerId": "%s"}],
                         "teamB": [{"playerId": "%s"}, %s]}
                        """.formatted(UUID.randomUUID(), ZonedDateTime.now(BRASILIA).minusHours(1).toOffsetDateTime(),
                        a, b, c, last)));
    }
}
