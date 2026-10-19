package com.lucchetta;

import com.lucchetta.configuration.security.AdminAwareAuthoritiesConverter;
import com.lucchetta.core.domain.league.DefaultLeague;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Base for API integration tests: full application, real PostgreSQL (one container shared by all
 * test classes) and simulated Firebase tokens.
 */
@SpringBootTest(properties = {
        "app.security.firebase-project-id=test-project",
        "app.security.admin-subject=" + IntegrationTest.ADMIN_UID,
        "app.cors.allowed-origins=" + IntegrationTest.WEB_ORIGIN + ", http://localhost:5000"
})
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    protected static final String ADMIN_UID = "admin-uid";
    protected static final String WEB_ORIGIN = "https://web.example.test";

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table device_token, notification, match_audit, match_participant, match, season_audit,"
                + " season, league_invitation, league_join_request, league_member, league, app_user cascade");
        // The migrated league (V6), which the endpoints without a league are bound to.
        jdbcTemplate.update("""
                insert into league (id, name, visibility, created_at, updated_at)
                values (?, ?, 'PUBLIC', now(), now())
                """, DefaultLeague.ID, DefaultLeague.NAME);
    }

    protected static RequestPostProcessor token(String subject, String name, String email) {
        return jwt()
                .jwt(builder -> {
                    builder.subject(subject);
                    if (name != null) {
                        builder.claim("name", name);
                    }
                    if (email != null) {
                        builder.claim("email", email);
                    }
                })
                .authorities(new AdminAwareAuthoritiesConverter(ADMIN_UID));
    }

    protected static RequestPostProcessor adminToken() {
        return token(ADMIN_UID, "Admin", null);
    }

    protected static RequestPostProcessor userToken() {
        return token("user-uid", "Jogador Comum", null);
    }
}
