package com.beachtennis.ranking.entrypoint.security;

import com.beachtennis.ranking.configuration.security.AdminAwareAuthoritiesConverter;
import com.beachtennis.ranking.core.domain.AuthIdentity;
import com.beachtennis.ranking.core.domain.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Translates the authenticated token into the provider-independent {@link AuthenticatedUser}.
 */
@Component
public class CurrentUserProvider {

    static final String PROVIDER = "firebase";

    public AuthenticatedUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("Request is not authenticated with a JWT");
        }
        Jwt jwt = token.getToken();
        boolean admin = token.getAuthorities().stream()
                .anyMatch(a -> AdminAwareAuthoritiesConverter.ROLE_ADMIN.equals(a.getAuthority()));
        return new AuthenticatedUser(
                new AuthIdentity(PROVIDER, jwt.getSubject()),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                admin);
    }
}
