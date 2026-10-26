package com.lucchetta.dataprovider.database.player;

import com.lucchetta.dataprovider.cache.CaffeineLeagueMatchCache;
import com.lucchetta.core.domain.AuthIdentity;
import com.lucchetta.core.domain.DisplayName;
import com.lucchetta.core.domain.FullName;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.port.PlayerRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PlayerRepositoryAdapter implements PlayerRepository {

    private final PlayerJpaRepository jpaRepository;
    private final CaffeineLeagueMatchCache leagueMatchCache;

    public PlayerRepositoryAdapter(PlayerJpaRepository jpaRepository, CaffeineLeagueMatchCache leagueMatchCache) {
        this.jpaRepository = jpaRepository;
        this.leagueMatchCache = leagueMatchCache;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Player> findByIdentity(AuthIdentity identity) {
        return jpaRepository.findByAuthProviderAndAuthSubject(identity.provider(), identity.subject())
                .map(PlayerRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Player> findAllByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllById(ids).stream().map(PlayerRepositoryAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Player> findByEmail(String email) {
        return jpaRepository.findByEmailIgnoringCase(email).stream().map(PlayerRepositoryAdapter::toDomain).toList();
    }

    @Override
    @Transactional
    public void insertIfAbsent(Player player) {
        jpaRepository.insertIfAbsent(
                player.id(),
                player.identity().provider(),
                player.identity().subject(),
                player.email(),
                player.displayName().value(),
                player.fullName() == null ? null : player.fullName().value(),
                player.createdAt(),
                player.updatedAt());
    }

    @Override
    @Transactional
    public Player update(Player player) {
        PlayerEntity entity = jpaRepository.findById(player.id())
                .orElseThrow(() -> new IllegalStateException("Player not found: " + player.id()));
        if (!entity.getDisplayName().equals(player.displayName().value())) {
            leagueMatchCache.evictAll(); // names are shown in every league's matches
        }
        entity.setDisplayName(player.displayName().value());
        entity.setFullName(player.fullName() == null ? null : player.fullName().value());
        entity.setUpdatedAt(player.updatedAt());
        return toDomain(jpaRepository.save(entity));
    }

    private static Player toDomain(PlayerEntity entity) {
        return new Player(
                entity.getId(),
                new AuthIdentity(entity.getAuthProvider(), entity.getAuthSubject()),
                entity.getEmail(),
                new DisplayName(entity.getDisplayName()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getFullName() == null ? null : new FullName(entity.getFullName()));
    }
}
