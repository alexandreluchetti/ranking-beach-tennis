package com.beachtennis.ranking.configuration.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

/**
 * Validation rules for Firebase ID tokens: signature (checked by the decoder), expiry, issuer
 * {@code https://securetoken.google.com/<projectId>}, audience equal to the project ID and a
 * non-empty subject.
 */
public final class FirebaseTokenValidators {

    public static final String JWK_SET_URI =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

    private FirebaseTokenValidators() {
    }

    public static String issuer(String projectId) {
        return "https://securetoken.google.com/" + projectId;
    }

    public static OAuth2TokenValidator<Jwt> forProject(String projectId) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer(projectId)),
                jwt -> jwt.getAudience() != null && jwt.getAudience().contains(projectId)
                        ? OAuth2TokenValidatorResult.success()
                        : failure("Invalid audience"),
                jwt -> jwt.getSubject() != null && !jwt.getSubject().isBlank()
                        ? OAuth2TokenValidatorResult.success()
                        : failure("Missing subject"));
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
