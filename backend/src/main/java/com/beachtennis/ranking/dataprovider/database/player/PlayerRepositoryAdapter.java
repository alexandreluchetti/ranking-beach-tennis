package com.beachtennis.ranking.dataprovider.database.player;

import com.beachtennis.ranking.core.domain.AuthIdentity;
import com.beachtennis.ranking.core.domain.DisplayName;
import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.port.PlayerRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
