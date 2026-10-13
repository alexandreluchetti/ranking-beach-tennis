package com.lucchetta.configuration.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAwareAuthoritiesConverterTest {

    @Test
    void grantsAdminOnlyToConfiguredSubject() {
        var converter = new AdminAwareAuthoritiesConverter("admin-uid");

        assertThat(names(converter.convert(jwt("admin-uid")))).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        assertThat(names(converter.convert(jwt("other-uid")))).containsExactly("ROLE_USER");
    }

    @Test
    void noAdminWhenNotConfigured() {
        assertThat(names(new AdminAwareAuthoritiesConverter("").convert(jwt("admin-uid")))).containsExactly("ROLE_USER");
        assertThat(names(new AdminAwareAuthoritiesConverter(null).convert(jwt("admin-uid")))).containsExactly("ROLE_USER");
    }

    @Test
    void ignoresRoleClaimsInsideToken() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("user-uid")
                .claim("roles", "ADMIN").claim("admin", true).build();

        assertThat(names(new AdminAwareAuthoritiesConverter("admin-uid").convert(jwt))).containsExactly("ROLE_USER");
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject(subject).build();
    }

    private static Collection<String> names(Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
