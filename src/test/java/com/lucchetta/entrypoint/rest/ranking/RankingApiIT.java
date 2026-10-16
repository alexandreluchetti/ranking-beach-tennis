package com.lucchetta.entrypoint.rest.ranking;

import com.lucchetta.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RankingApiIT extends IntegrationTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private String ana;
    private String beto;
    private String caio;
    private String dani;

    @BeforeEach
    void createPlayers() throws Exception {
        ana = playerId("uid-ana", "Ana");
        beto = playerId("uid-beto", "Beto");
        caio = playerId("uid-caio", "Caio");
        dani = playerId("uid-dani", "Dani");
    }

    @Test
    void noActiveSeasonMeansNoCurrentRanking() throws Exception {
        mockMvc.perform(get("/api/seasons/current/ranking").with(userToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void currentRankingFollowsApprovedPointsAndTieBreaks() throws Exception {
        String seasonId = createActiveSeason();
        register(ana, beto, caio, dani, 6, 4, 90);          // Ana/Beto +9, Caio/Dani +4
        register(caio, ana, beto, dani, 7, 6, 60);          // Caio/Ana +10, Beto/Dani +6

        mockMvc.perform(get("/api/seasons/current/ranking").with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season.id").value(seasonId))
                // the admin and the viewer joined the Arena through the endpoints without a league: listed with zero
                .andExpect(jsonPath("$.entries.length()").value(6))
                .andExpect(jsonPath("$.entries[0].displayName").value("Ana"))
                .andExpect(jsonPath("$.entries[0].position").value(1))
                .andExpect(jsonPath("$.entries[0].points").value(19))
                .andExpect(jsonPath("$.entries[0].wins").value(2))
                .andExpect(jsonPath("$.entries[0].losses").value(0))
                .andExpect(jsonPath("$.entries[0].matchesPlayed").value(2))
                .andExpect(jsonPath("$.entries[0].gameDifference").value(3))
                .andExpect(jsonPath("$.entries[1].displayName").value("Beto"))
                .andExpect(jsonPath("$.entries[1].points").value(15))
                .andExpect(jsonPath("$.entries[2].displayName").value("Caio"))
                .andExpect(jsonPath("$.entries[2].points").value(14))
                .andExpect(jsonPath("$.entries[3].displayName").value("Dani"))
                .andExpect(jsonPath("$.entries[3].points").value(10))
                .andExpect(jsonPath("$.entries[4].displayName").value("Admin"))
                .andExpect(jsonPath("$.entries[4].points").value(0));

        mockMvc.perform(get("/api/seasons/" + seasonId + "/ranking").with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].displayName").value("Ana"));
    }

    @Test
    void historyFiltersByOutcomeAndPlayer() throws Exception {
        createActiveSeason();
        register(ana, beto, caio, dani, 6, 4, 90);
        registerWithGuest(ana, caio, dani, 6, 1, 60);

        mockMvc.perform(get("/api/matches").with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].notCountedReason").value("GUEST_PRESENT"))
                .andExpect(jsonPath("$.hasNext").value(false));
        mockMvc.perform(get("/api/matches").param("outcome", "COUNTED").with(userToken()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].counted").value(true));
        mockMvc.perform(get("/api/matches").param("outcome", "NOT_COUNTED").param("outcome", "VOIDED")
                        .with(userToken()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].teamA[1].guest").value(true));
        mockMvc.perform(get("/api/matches").param("playerId", beto).with(userToken()))
                .andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(get("/api/matches").param("size", "1").with(userToken()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(true));
        mockMvc.perform(get("/api/matches").param("outcome", "SOMETHING").with(userToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void playersAndProfile() throws Exception {
        createActiveSeason();
        register(ana, beto, caio, dani, 6, 4, 90);
        registerWithGuest(ana, caio, dani, 6, 1, 60);

        mockMvc.perform(get("/api/players").with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].displayName").value("Admin"))
                .andExpect(jsonPath("$[1].displayName").value("Ana"));

        mockMvc.perform(get("/api/players/" + ana + "/profile").with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana"))
                .andExpect(jsonPath("$.currentSeason.position").value(1))
                .andExpect(jsonPath("$.currentSeason.stats.points").value(9))
                // The guest match counts only for the career (D-004).
                .andExpect(jsonPath("$.currentSeason.stats.matchesPlayed").value(1))
                .andExpect(jsonPath("$.currentSeason.stats.wins").value(1))
                .andExpect(jsonPath("$.currentSeason.stats.gameDifference").value(2))
                .andExpect(jsonPath("$.career.matchesPlayed").value(2))
                .andExpect(jsonPath("$.career.gameDifference").value(7))
                .andExpect(jsonPath("$.pastSeasons.length()").value(0));
        mockMvc.perform(get("/api/players/" + UUID.randomUUID() + "/profile").with(userToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }

    private void register(String a1, String a2, String b1, String b2, int teamAGames, int teamBGames,
                          int minutesAgo) throws Exception {
        submitMatch(participant(a1), participant(a2), participant(b1), participant(b2), teamAGames, teamBGames, minutesAgo);
    }

    private void registerWithGuest(String a1, String b1, String b2, int teamAGames, int teamBGames,
                                   int minutesAgo) throws Exception {
        submitMatch(participant(a1), "{\"guest\": true}", participant(b1), participant(b2), teamAGames, teamBGames,
                minutesAgo);
    }

    private void submitMatch(String a1, String a2, String b1, String b2, int teamAGames, int teamBGames, int minutesAgo)
            throws Exception {
        String playedAt = ZonedDateTime.now(BRASILIA).minusMinutes(minutesAgo).toOffsetDateTime().toString();
        mockMvc.perform(post("/api/matches").with(token("uid-ana", "Ana", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "%s", "playedAt": "%s", "format": "STANDARD",
                                 "teamAGames": %d, "teamBGames": %d,
                                 "teamA": [%s, %s], "teamB": [%s, %s], "confirmPossibleDuplicate": true}
                                """.formatted(UUID.randomUUID(), playedAt, teamAGames, teamBGames, a1, a2, b1, b2)))
                .andExpect(status().isCreated());
    }

    private static String participant(String playerId) {
        return "{\"playerId\": \"" + playerId + "\"}";
    }

    /** Signs in (app before leagues): any endpoint without a league joins the "Beach Tennis Arena". */
    private String playerId(String uid, String name) throws Exception {
        mockMvc.perform(get("/api/seasons").with(token(uid, name, null))).andExpect(status().isOk());
        return JsonPath.read(mockMvc.perform(get("/api/me").with(token(uid, name, null)))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String createActiveSeason() throws Exception {
        LocalDate today = LocalDate.now(BRASILIA);
        return JsonPath.read(mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Primavera\", \"startDate\": \"%s\", \"endDate\": \"%s\"}"
                                .formatted(today.minusDays(30), today.plusDays(30))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }
}
