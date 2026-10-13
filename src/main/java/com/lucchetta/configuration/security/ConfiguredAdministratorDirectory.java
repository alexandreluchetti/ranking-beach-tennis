package com.lucchetta.configuration.security;

import com.lucchetta.core.domain.AuthIdentity;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.port.AdministratorDirectory;
import com.lucchetta.core.port.PlayerRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * The administrator is whoever's authentication subject is configured in the deployment
 * ({@code ADMIN_AUTH_SUBJECT}).
 */
@Component
public class ConfiguredAdministratorDirectory implements AdministratorDirectory {

    static final String PROVIDER = "firebase";

    private final SecurityProperties properties;
    private final PlayerRepository playerRepository;

    public ConfiguredAdministratorDirectory(SecurityProperties properties, PlayerRepository playerRepository) {
        this.properties = properties;
        this.playerRepository = playerRepository;
    }

    @Override
    public Optional<UUID> administratorPlayerId() {
        String subject = properties.adminSubject();
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        return playerRepository.findByIdentity(new AuthIdentity(PROVIDER, subject.strip())).map(Player::id);
    }
}
