package com.lucchetta.entrypoint.rest.league;

import com.jayway.jsonpath.JsonPath;
import com.lucchetta.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeagueEntryApiIT extends IntegrationTest {

    private static final RequestPostProcessor ANA = token("ana-uid", "Ana", "ana@example.com");
    private static final RequestPostProcessor BRUNO = token("bruno-uid", "Bruno", "bruno@example.com");
    private static final RequestPostProcessor CARLA = token("carla-uid", "Carla", "Carla@Example.com");

    @Test
    void joinRequestIsAnsweredFromTheAdministratorsNotification() throws Exception {
        String league = createPrivateLeague();

        mockMvc.perform(post("/api/leagues/" + league + "/join-requests").with(BRUNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinRequestPending").value(true))
                .andExpect(jsonPath("$.myRole").isEmpty());
        mockMvc.perform(post("/api/leagues/" + league + "/join-requests").with(BRUNO))
                .andExpect(status().isOk());

        String inbox = mockMvc.perform(get("/api/notifications").with(ANA))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("LEAGUE_JOIN_REQUESTED"))
                .andExpect(jsonPath("$.items[0].body").value("Bruno pediu para entrar na liga Liga Fechada."))
                .andExpect(jsonPath("$.items[0].leagueId").value(league))
                .andExpect(jsonPath("$.items[0].matchId").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(inbox, "$.items[0].joinRequestId");
        mockMvc.perform(get("/api/leagues/" + league + "/join-requests").with(ANA))
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].displayName").value("Bruno"));
        mockMvc.perform(get("/api/leagues/" + league + "/join-requests").with(BRUNO))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/leagues/" + league + "/join-requests/" + requestId + "/accept").with(ANA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/leagues/" + league).with(BRUNO))
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.joinRequestPending").value(false));
        mockMvc.perform(get("/api/notifications").with(BRUNO))
                .andExpect(jsonPath("$.items[0].type").value("LEAGUE_JOIN_REQUEST_ACCEPTED"))
                .andExpect(jsonPath("$.items[0].title").value("Pedido aceito"));
        mockMvc.perform(post("/api/leagues/" + league + "/join-requests/" + requestId + "/reject").with(ANA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOIN_REQUEST_NOT_PENDING"));
    }

    @Test
    void requesterCancelsAndTheDatabaseKeepsOnePendingRequest() throws Exception {
        String league = createPrivateLeague();
        mockMvc.perform(post("/api/leagues/" + league + "/join-requests").with(BRUNO)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/leagues/" + league + "/join-requests/mine").with(BRUNO))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/leagues/" + league + "/join-requests/mine").with(BRUNO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOIN_REQUEST_NOT_FOUND"));
        mockMvc.perform(post("/api/leagues/" + league + "/join-requests").with(BRUNO)).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForList("select status from league_join_request order by created_at, status",
                String.class)).containsExactlyInAnyOrder("CANCELLED", "PENDING");
    }

    @Test
    void invitationByEmailIsAcceptedFromTheInviteesNotification() throws Exception {
        String league = createPrivateLeague();
        mockMvc.perform(get("/api/me").with(CARLA)).andExpect(status().isOk());

        mockMvc.perform(post("/api/leagues/" + league + "/invitations").with(ANA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"carla@example.com\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/leagues/" + league + "/invitations").with(ANA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"ninguem@example.com\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/leagues/" + league + "/invitations").with(ANA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"invalido\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"));

        String inbox = mockMvc.perform(get("/api/notifications").with(CARLA))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("LEAGUE_INVITATION_RECEIVED"))
                .andExpect(jsonPath("$.items[0].body").value("Ana convidou você para a liga Liga Fechada."))
                .andReturn().getResponse().getContentAsString();
        String invitationId = JsonPath.read(inbox, "$.items[0].invitationId");
        mockMvc.perform(get("/api/me/invitations").with(CARLA))
                .andExpect(jsonPath("$[0].id").value(invitationId))
                .andExpect(jsonPath("$[0].league.name").value("Liga Fechada"))
                .andExpect(jsonPath("$[0].league.memberCount").value(1))
                .andExpect(jsonPath("$[0].invitedByName").value("Ana"));
        mockMvc.perform(post("/api/me/invitations/" + invitationId + "/accept").with(BRUNO))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/me/invitations/" + invitationId + "/accept").with(CARLA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.memberCount").value(2));
        mockMvc.perform(get("/api/me/invitations").with(CARLA))
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbcTemplate.queryForObject("select count(*) from league_invitation", Integer.class)).isEqualTo(1);
    }

    @Test
    void inviteCodeJoinsPrivateLeaguesAndRegeneratingInvalidatesIt() throws Exception {
        String league = createPrivateLeague();
        mockMvc.perform(get("/api/leagues/" + league + "/invite-code").with(ANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isEmpty());
        mockMvc.perform(post("/api/leagues/" + league + "/invite-code").with(BRUNO))
                .andExpect(status().isForbidden());
        String first = JsonPath.read(mockMvc.perform(post("/api/leagues/" + league + "/invite-code").with(ANA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.code");
        assertThat(first).matches("[A-HJKMNP-Z2-9]{10}");

        mockMvc.perform(post("/api/leagues/join-by-code").with(BRUNO).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"" + first.toLowerCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(league))
                .andExpect(jsonPath("$.myRole").value("MEMBER"));

        mockMvc.perform(post("/api/leagues/" + league + "/invite-code").with(ANA)).andExpect(status().isOk());
        mockMvc.perform(post("/api/leagues/join-by-code").with(CARLA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"" + first + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_INVALID"));
    }

    private String createPrivateLeague() throws Exception {
        String body = mockMvc.perform(post("/api/leagues").with(ANA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Liga Fechada\", \"visibility\": \"PRIVATE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
