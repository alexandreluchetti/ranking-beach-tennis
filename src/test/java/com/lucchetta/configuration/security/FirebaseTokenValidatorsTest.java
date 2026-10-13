package com.lucchetta.configuration.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseTokenValidatorsTest {

    private static final String PROJECT = "my-project";
    private final OAuth2TokenValidator<Jwt> validator = FirebaseTokenValidators.forProject(PROJECT);

    @Test
    void acceptsTokenForProject() {
        assertThat(validator.validate(token(FirebaseTokenValidators.issuer(PROJECT), PROJECT, "uid", 3600)).hasErrors())
                .isFalse();
    }

    @Test
    void rejectsWrongIssuer() {
        assertThat(validator.validate(token("https://securetoken.google.com/other", PROJECT, "uid", 3600)).hasErrors())
                .isTrue();
    }

    @Test
    void rejectsWrongAudience() {
        assertThat(validator.validate(token(FirebaseTokenValidators.issuer(PROJECT), "other", "uid", 3600)).hasErrors())
                .isTrue();
    }

    @Test
    void rejectsExpiredToken() {
        assertThat(validator.validate(token(FirebaseTokenValidators.issuer(PROJECT), PROJECT, "uid", -3600)).hasErrors())
                .isTrue();
    }

    @Test
    void rejectsBlankSubject() {
        assertThat(validator.validate(token(FirebaseTokenValidators.issuer(PROJECT), PROJECT, " ", 3600)).hasErrors())
                .isTrue();
    }

    private static Jwt token(String issuer, String audience, String subject, long expiresInSeconds) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .claim("iss", issuer)
                .audience(List.of(audience))
                .subject(subject)
                .issuedAt(now.minusSeconds(Math.abs(expiresInSeconds) + 10))
                .expiresAt(now.plusSeconds(expiresInSeconds))
                .build();
    }
}
