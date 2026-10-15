package com.lucchetta.entrypoint.rest.match;

import com.lucchetta.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchApiIT extends IntegrationTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private String ana;
    private String beto;
    private String caio;
    private String dani;
    private OffsetDateTime playedAt;

    @BeforeEach
    void createPlayers() throws Exception {
        ana = playerId("uid-ana", "Ana");
        beto = playerId("uid-beto", "Beto");
        caio = playerId("uid-caio", "Caio");
        dani = playerId("uid-dani", "Dani");
        playedAt = ZonedDateTime.now(BRASILIA).minusMinutes(30).toOffsetDateTime();
    }

    @Test
    void registersCountedMatchWithApprovedPoints() throws Exception {
        createActiveSeason();
        String id = UUID.randomUUID().toString();

        register(body(id, 6, 4, player(ana), player(beto), player(caio), player(dani), false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.teamAGames").value(6))
                .andExpect(jsonPath("$.teamBGames").value(4))
                .andExpect(jsonPath("$.winner").value("A"))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.counted").value(true))
                .andExpect(jsonPath("$.season.name").value("Primavera"))
                .andExpect(jsonPath("$.teamA[0].displayName").value("Ana"))
                .andExpect(jsonPath("$.teamA[0].points").value(9))
                .andExpect(jsonPath("$.teamA[1].points").value(9))
                .andExpect(jsonPath("$.teamB[0].points").value(4))
                .andExpect(jsonPath("$.teamB[1].points").value(4));

        assertThat(jdbcTemplate.queryForObject("select count(*) from match_participant where match_id = ?::uuid",
                Integer.class, id)).isEqualTo(4);
        mockMvc.perform(get("/api/matches/" + id).with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamB[1].displayName").value("Dani"));
    }

    @Test
    void tiebreakMatchStoresTiebreakForHistoryOnly() throws Exception {
        createActiveSeason();

        register(body(UUID.randomUUID().toString(), 6, 7, "7", "9",
                player(ana), player(beto), player(caio), player(dani), false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tiebreakTeamAPoints").value(7))
                .andExpect(jsonPath("$.tiebreakTeamBPoints").value(9))
                .andExpect(jsonPath("$.winner").value("B"))
                .andExpect(jsonPath("$.teamB[0].points").value(10))
                .andExpect(jsonPath("$.teamA[0].points").value(6));
    }

    @Test
    void resendIsIdempotentAndDifferentContentConflicts() throws Exception {
        String id = UUID.randomUUID().toString();
        String original = body(id, 6, 4, player(ana), player(beto), player(caio), player(dani), false);

        register(original).andExpect(status().isCreated());
        register(original).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        register(body(id, 6, 3, player(ana), player(beto), player(caio), player(dani), false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_ID_CONFLICT"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from match", Integer.class)).isEqualTo(1);
    }

    @Test
    void possibleDuplicateNeedsConfirmation() throws Exception {
        String first = UUID.randomUUID().toString();
        register(body(first, 6, 4, player(ana), player(beto), player(caio), player(dani), false))
                .andExpect(status().isCreated());

        String second = UUID.randomUUID().toString();
        register(body(second, 6, 2, player(caio), player(dani), player(beto), player(ana), false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POSSIBLE_DUPLICATE_MATCH"))
                .andExpect(jsonPath("$.existingMatchIds[0]").value(first));
        register(body(second, 6, 2, player(caio), player(dani), player(beto), player(ana), true))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("select count(*) from match", Integer.class)).isEqualTo(2);
    }

    @Test
    void guestMatchIsSavedWithoutPoints() throws Exception {
        createActiveSeason();

        register(body(UUID.randomUUID().toString(), 6, 4, player(ana), guest("Zé"), player(caio), guest(null), false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.counted").value(false))
                .andExpect(jsonPath("$.notCountedReason").value("GUEST_PRESENT"))
                .andExpect(jsonPath("$.teamA[0].points").value(0))
                .andExpect(jsonPath("$.teamA[1].guest").value(true))
                .andExpect(jsonPath("$.teamA[1].guestName").value("Zé"))
                .andExpect(jsonPath("$.teamB[1].guestName").doesNotExist())
                .andExpect(jsonPath("$.teamB[0].points").value(0));
    }

    @Test
    void matchWithoutSeasonIsKeptForHistory() throws Exception {
        register(body(UUID.randomUUID().toString(), 4, 2, player(ana), player(beto), player(caio), player(dani), false)
                .replace("STANDARD", "SHORT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.format").value("SHORT"))
                .andExpect(jsonPath("$.counted").value(false))
                .andExpect(jsonPath("$.notCountedReason").value("NO_SEASON"))
                .andExpect(jsonPath("$.season").doesNotExist());
    }

    @Test
    void validationErrorsAreReportedInPortuguese() throws Exception {
        register(body(UUID.randomUUID().toString(), 6, 5, player(ana), player(beto), player(caio), player(dani), false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCORE"))
                .andExpect(jsonPath("$.detail").value("Placar inválido para o formato escolhido."));
        register(body(UUID.randomUUID().toString(), 6, 4, player(ana), player(beto), player(ana), player(dani), false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PARTICIPANT"));
        register(body(UUID.randomUUID().toString(), 6, 4, player(ana), player(UUID.randomUUID().toString()),
                player(caio), player(dani), false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_LEAGUE_MEMBER"));
        register(body(UUID.randomUUID().toString(), 6, 4, player(ana), "{\"playerId\": \"" + beto + "\", \"guest\": true}",
                player(caio), player(dani), false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARTICIPANTS"));

        playedAt = ZonedDateTime.now(BRASILIA).plusHours(2).toOffsetDateTime();
        register(body(UUID.randomUUID().toString(), 6, 4, player(ana), player(beto), player(caio), player(dani), false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MATCH_IN_FUTURE"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from match", Integer.class)).isZero();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), 6, 4,
                                player(ana), player(beto), player(caio), player(dani), false)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/matches/" + UUID.randomUUID()).with(userToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_NOT_FOUND"));
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/matches").with(token("uid-ana", "Ana", null))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String body(String id, int teamAGames, int teamBGames, String a1, String a2, String b1, String b2,
                        boolean confirm) {
        return body(id, teamAGames, teamBGames, "null", "null", a1, a2, b1, b2, confirm);
    }

    private String body(String id, int teamAGames, int teamBGames, String tiebreakA, String tiebreakB,
                        String a1, String a2, String b1, String b2, boolean confirm) {
        return """
                {"id": "%s", "playedAt": "%s", "format": "STANDARD",
                 "teamAGames": %d, "teamBGames": %d,
                 "tiebreakTeamAPoints": %s, "tiebreakTeamBPoints": %s,
                 "teamA": [%s, %s], "teamB": [%s, %s],
                 "confirmPossibleDuplicate": %s}
                """.formatted(id, playedAt, teamAGames, teamBGames, tiebreakA, tiebreakB, a1, a2, b1, b2, confirm);
    }

    private static String player(String id) {
        return "{\"playerId\": \"" + id + "\"}";
    }

    private static String guest(String name) {
        return name == null ? "{\"guest\": true}" : "{\"guest\": true, \"guestName\": \"" + name + "\"}";
    }

    /** Signs in (app before leagues): any endpoint without a league joins the "Beach Tennis Arena". */
    private String playerId(String uid, String name) throws Exception {
        mockMvc.perform(get("/api/seasons").with(token(uid, name, null))).andExpect(status().isOk());
        return JsonPath.read(mockMvc.perform(get("/api/me").with(token(uid, name, null)))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void createActiveSeason() throws Exception {
        LocalDate today = LocalDate.now(BRASILIA);
        mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Primavera\", \"startDate\": \"%s\", \"endDate\": \"%s\"}"
                                .formatted(today.minusDays(30), today.plusDays(30))))
                .andExpect(status().isCreated());
    }
}
