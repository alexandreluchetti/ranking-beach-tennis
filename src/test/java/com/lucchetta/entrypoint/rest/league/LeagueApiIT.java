package com.lucchetta.entrypoint.rest.league;

import com.jayway.jsonpath.JsonPath;
import com.lucchetta.IntegrationTest;
import com.lucchetta.core.domain.league.DefaultLeague;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeagueApiIT extends IntegrationTest {

    private static final RequestPostProcessor ANA = token("ana-uid", "Ana", null);
    private static final RequestPostProcessor BRUNO = token("bruno-uid", "Bruno", null);
    private static final RequestPostProcessor CARLA = token("carla-uid", "Carla", null);

    @Test
    void creatorAdministersTheNewLeague() throws Exception {
        mockMvc.perform(post("/api/leagues").with(ANA).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Liga do Clube", "description": "Quartas à noite", "visibility": "PRIVATE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Liga do Clube"))
                .andExpect(jsonPath("$.description").value("Quartas à noite"))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.memberLimit").value(50))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.myRole").value("ADMIN"))
                .andExpect(jsonPath("$.canAdminister").value(true));

        mockMvc.perform(get("/api/leagues/mine").with(ANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Liga do Clube"));
    }

    @Test
    void invalidOrDuplicateLeaguesAreRejectedWithMessages() throws Exception {
        createLeague(ANA, "Liga do Clube", "PUBLIC");

        mockMvc.perform(post("/api/leagues").with(BRUNO).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"liga DO clube\", \"visibility\": \"PUBLIC\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAGUE_NAME_TAKEN"))
                .andExpect(jsonPath("$.detail").value("Já existe uma liga com este nome."));
        mockMvc.perform(post("/api/leagues").with(BRUNO).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"AB\", \"visibility\": \"PUBLIC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LEAGUE_NAME"));
        mockMvc.perform(post("/api/leagues").with(BRUNO).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Liga Nova\", \"visibility\": \"SECRET\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchShowsSummariesWithMemberCountAndHidesArchivedLeagues() throws Exception {
        String clube = createLeague(ANA, "Liga do Clube", "PRIVATE");
        createLeague(ANA, "100% Areia_Fina", "PUBLIC");
        String archived = createLeague(ANA, "Liga Antiga", "PUBLIC");
        mockMvc.perform(post("/api/leagues/" + archived + "/archive").with(ANA)).andExpect(status().isOk());

        mockMvc.perform(get("/api/leagues").param("query", "LIGA").with(BRUNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(clube))
                .andExpect(jsonPath("$.items[0].memberCount").value(1))
                .andExpect(jsonPath("$.items[0].myRole").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
        mockMvc.perform(get("/api/leagues").param("query", "0% areia_").with(BRUNO))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("100% Areia_Fina"));
        mockMvc.perform(get("/api/leagues").param("size", "2").with(BRUNO))
                .andExpect(jsonPath("$.items[0].name").value("100% Areia_Fina"))
                .andExpect(jsonPath("$.items[1].name").value(DefaultLeague.NAME))
                .andExpect(jsonPath("$.hasNext").value(true));
        mockMvc.perform(get("/api/leagues").param("archived", "true").with(BRUNO))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/leagues").param("archived", "true").with(adminToken()))
                .andExpect(jsonPath("$.items[0].id").value(archived));
    }

    @Test
    void publicLeaguesAreJoinedDirectlyAndPrivateOnesAreNot() throws Exception {
        String publicLeague = createLeague(ANA, "Liga Aberta", "PUBLIC");
        String privateLeague = createLeague(ANA, "Liga Fechada", "PRIVATE");

        mockMvc.perform(post("/api/leagues/" + publicLeague + "/join").with(BRUNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.canAdminister").value(false));
        mockMvc.perform(post("/api/leagues/" + privateLeague + "/join").with(BRUNO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAGUE_PRIVATE"));
    }

    @Test
    void contentIsForMembersOnly() throws Exception {
        String league = createLeague(ANA, "Liga Fechada", "PRIVATE");

        mockMvc.perform(get("/api/leagues/" + league).with(BRUNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Liga Fechada"))
                .andExpect(jsonPath("$.memberCount").value(1));
        mockMvc.perform(get("/api/leagues/" + league + "/members").with(BRUNO))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/leagues/" + league + "/members").with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Ana"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    @Test
    void administratorsManageMembersAndRoles() throws Exception {
        String league = createLeague(ANA, "Liga Aberta", "PUBLIC");
        String bruno = joinAndGetPlayerId(league, BRUNO);
        String carla = joinAndGetPlayerId(league, CARLA);

        mockMvc.perform(put("/api/leagues/" + league + "/members/" + bruno + "/role").with(CARLA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/leagues/" + league + "/members/" + bruno + "/role").with(ANA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Bruno"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(delete("/api/leagues/" + league + "/members/" + carla).with(BRUNO))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/leagues/" + league + "/members").with(ANA))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[1].role").value("ADMIN"));

        mockMvc.perform(post("/api/leagues/" + league + "/leave").with(ANA)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/leagues/" + league + "/leave").with(BRUNO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAST_LEAGUE_ADMIN"))
                .andExpect(jsonPath("$.detail").value("A liga precisa ter ao menos um administrador."));
    }

    @Test
    void leagueAdministratorEditsAndArchivesSystemAdministratorUnarchives() throws Exception {
        String league = createLeague(ANA, "Liga Aberta", "PUBLIC");

        mockMvc.perform(patch("/api/leagues/" + league).with(ANA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"Sábados\", \"visibility\": \"PRIVATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Liga Aberta"))
                .andExpect(jsonPath("$.description").value("Sábados"))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));
        mockMvc.perform(patch("/api/leagues/" + league).with(BRUNO).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Tomada\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/leagues/" + league + "/archive").with(ANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mockMvc.perform(patch("/api/leagues/" + league).with(ANA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Outra\"}"))
                .andExpect(jsonPath("$.code").value("LEAGUE_ARCHIVED"));
        mockMvc.perform(post("/api/leagues/" + league + "/unarchive").with(ANA))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/leagues/" + league + "/unarchive").with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.canAdminister").value(true))
                .andExpect(jsonPath("$.myRole").isEmpty());
    }

    @Test
    void creationLimitCountsOnlyActiveLeagues() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createLeague(ANA, "Liga " + i, "PUBLIC");
        }

        mockMvc.perform(post("/api/leagues").with(ANA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Liga 6\", \"visibility\": \"PUBLIC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAGUE_LIMIT_REACHED"));
    }

    @Test
    void unknownLeagueIsNotFound() throws Exception {
        mockMvc.perform(get("/api/leagues/00000000-0000-0000-0000-00000000abcd").with(ANA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEAGUE_NOT_FOUND"));
    }

    private String createLeague(RequestPostProcessor user, String name, String visibility) throws Exception {
        String body = mockMvc.perform(post("/api/leagues").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"visibility\": \"" + visibility + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String joinAndGetPlayerId(String league, RequestPostProcessor user) throws Exception {
        mockMvc.perform(post("/api/leagues/" + league + "/join").with(user)).andExpect(status().isOk());
        String body = mockMvc.perform(get("/api/me").with(user)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
