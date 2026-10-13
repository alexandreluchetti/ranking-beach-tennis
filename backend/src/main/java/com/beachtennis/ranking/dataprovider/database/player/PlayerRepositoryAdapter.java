package com.beachtennis.ranking.dataprovider.database.player;

import com.beachtennis.ranking.core.domain.AuthIdentity;
import com.beachtennis.ranking.core.domain.DisplayName;
import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.port.PlayerRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PlayerRepositoryAdapter implements PlayerRepository {

    private final PlayerJpaRepository jpaRepository;

    public PlayerRepositoryAdapter(PlayerJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
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
    public List<Player> findAll() {
        return jpaRepository.findAll().stream().map(PlayerRepositoryAdapter::toDomain).toList();
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
                player.createdAt(),
                player.updatedAt());
    }

    @Override
    @Transactional
    public Player update(Player player) {
        PlayerEntity entity = jpaRepository.findById(player.id())
                .orElseThrow(() -> new IllegalStateException("Player not found: " + player.id()));
        entity.setDisplayName(player.displayName().value());
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
                entity.getUpdatedAt());
    }
}
