package com.beachtennis.ranking.entrypoint.rest.me;

import com.beachtennis.ranking.configuration.security.AdminAwareAuthoritiesConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.firebase-project-id=test-project",
        "app.security.admin-subject=" + MeApiIT.ADMIN_UID
})
@AutoConfigureMockMvc
@Testcontainers
class MeApiIT {

    static final String ADMIN_UID = "admin-uid";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from app_user");
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void createsProfileOnFirstAccessOnlyOnce() throws Exception {
        mockMvc.perform(get("/api/me").with(token("uid-1", "Ana Souza", "ana@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.admin").value(false));
        mockMvc.perform(get("/api/me").with(token("uid-1", "Ana Souza", "ana@example.com")))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("select count(*) from app_user", Integer.class)).isEqualTo(1);
    }

    @Test
    void flagsOnlyConfiguredSubjectAsAdmin() throws Exception {
        mockMvc.perform(get("/api/me").with(token(ADMIN_UID, "Admin", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void renamesKeepingTheSameId() throws Exception {
        String id = com.jayway.jsonpath.JsonPath.read(
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
    void adminAreaIsForbiddenForRegularUsers() throws Exception {
        mockMvc.perform(get("/api/admin/anything").with(token("uid-1", "Ana", null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/anything").with(token(ADMIN_UID, "Admin", null)))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor token(String subject, String name, String email) {
        return jwt()
                .jwt(builder -> {
                    builder.subject(subject).claim("name", name);
                    if (email != null) {
                        builder.claim("email", email);
                    }
                })
                .authorities(new AdminAwareAuthoritiesConverter(ADMIN_UID));
    }
}
