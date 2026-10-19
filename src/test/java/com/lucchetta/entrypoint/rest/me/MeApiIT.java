package com.lucchetta.entrypoint.rest.me;

import com.lucchetta.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeApiIT extends IntegrationTest {

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void createsProfileOnFirstAccessOnlyOnce() throws Exception {
        mockMvc.perform(get("/api/me").with(token("uid-1", "Ana Souza", "ana@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana Souza"))
                .andExpect(jsonPath("$.fullName").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.admin").value(false));
        mockMvc.perform(get("/api/me").with(token("uid-1", "Ana Souza", "ana@example.com")))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("select count(*) from app_user", Integer.class)).isEqualTo(1);
    }

    @Test
    void flagsOnlyConfiguredSubjectAsAdmin() throws Exception {
        mockMvc.perform(get("/api/me").with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void renamesKeepingTheSameId() throws Exception {
        String id = JsonPath.read(
                mockMvc.perform(get("/api/me").with(token("uid-1", "Ana", null)))
                        .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/me").with(token("uid-1", "Ana", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"  Ana Paula \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.displayName").value("Ana Paula"));
    }

    @Test
    void rejectsInvalidDisplayNameWithPortugueseMessage() throws Exception {
        mockMvc.perform(patch("/api/me").with(token("uid-1", "Ana", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DISPLAY_NAME"))
                .andExpect(jsonPath("$.detail").value("O nome de exibição deve ter entre 2 e 40 caracteres."));
    }

    @Test
    void emailSignUpSetsTheFullNameAndNicknameAfterwards() throws Exception {
        mockMvc.perform(get("/api/me").with(token("uid-2", null, "teste@gmail.com")))
                .andExpect(jsonPath("$.displayName").value("teste"))
                .andExpect(jsonPath("$.fullName").isEmpty());

        mockMvc.perform(patch("/api/me").with(token("uid-2", null, "teste@gmail.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Alex\", \"fullName\": \"  Alexandre   Lucchetta \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alex"))
                .andExpect(jsonPath("$.fullName").value("Alexandre Lucchetta"));
        mockMvc.perform(patch("/api/me").with(token("uid-2", null, "teste@gmail.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Alexandre\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FULL_NAME"))
                .andExpect(jsonPath("$.detail")
                        .value("Informe o nome completo (nome e sobrenome), com 3 a 100 caracteres."));
    }

    @Test
    void adminAreaIsForbiddenForRegularUsers() throws Exception {
        mockMvc.perform(get("/api/admin/anything").with(userToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/anything").with(adminToken()))
                .andExpect(status().isNotFound());
    }
}
