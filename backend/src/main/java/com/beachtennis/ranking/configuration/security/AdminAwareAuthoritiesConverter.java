package com.beachtennis.ranking.configuration.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/**
 * Grants {@code ROLE_USER} to every authenticated caller and {@code ROLE_ADMIN} only to the
 * subject configured in the deployment. Roles are never read from token claims.
 */
public class AdminAwareAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final String adminSubject;

    public AdminAwareAuthoritiesConverter(String adminSubject) {
        this.adminSubject = adminSubject == null || adminSubject.isBlank() ? null : adminSubject.strip();
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (adminSubject != null && adminSubject.equals(jwt.getSubject())) {
            return List.of(new SimpleGrantedAuthority(ROLE_USER), new SimpleGrantedAuthority(ROLE_ADMIN));
        }
        return List.of(new SimpleGrantedAuthority(ROLE_USER));
    }
}
