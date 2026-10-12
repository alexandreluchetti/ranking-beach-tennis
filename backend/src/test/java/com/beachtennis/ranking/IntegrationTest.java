package com.beachtennis.ranking;

import com.beachtennis.ranking.configuration.security.AdminAwareAuthoritiesConverter;
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
        "app.security.admin-subject=" + IntegrationTest.ADMIN_UID
})
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    protected static final String ADMIN_UID = "admin-uid";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table season_audit, season, app_user cascade");
    }

    protected static RequestPostProcessor token(String subject, String name, String email) {
        return jwt()
                .jwt(builder -> {
                    builder.subject(subject).claim("name", name);
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
