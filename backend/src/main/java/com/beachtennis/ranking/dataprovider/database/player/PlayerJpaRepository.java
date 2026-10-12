package com.beachtennis.ranking.dataprovider.database.player;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface PlayerJpaRepository extends JpaRepository<PlayerEntity, UUID> {

    Optional<PlayerEntity> findByAuthProviderAndAuthSubject(String authProvider, String authSubject);

    @Modifying
    @Query(value = """
            insert into app_user (id, auth_provider, auth_subject, email, display_name, created_at, updated_at)
            values (:id, :authProvider, :authSubject, :email, :displayName, :createdAt, :updatedAt)
            on conflict (auth_provider, auth_subject) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id,
                        @Param("authProvider") String authProvider,
                        @Param("authSubject") String authSubject,
                        @Param("email") String email,
                        @Param("displayName") String displayName,
                        @Param("createdAt") Instant createdAt,
                        @Param("updatedAt") Instant updatedAt);
}
