package com.lucchetta.dataprovider.database.player;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PlayerJpaRepository extends JpaRepository<PlayerEntity, UUID> {

    Optional<PlayerEntity> findByAuthProviderAndAuthSubject(String authProvider, String authSubject);

    @Query("select p from PlayerEntity p where lower(p.email) = lower(:email)")
    List<PlayerEntity> findByEmailIgnoringCase(@Param("email") String email);

    @Modifying
    @Query(value = """
            insert into app_user (id, auth_provider, auth_subject, email, display_name, full_name, created_at,
                                  updated_at)
            values (:id, :authProvider, :authSubject, :email, :displayName, :fullName, :createdAt, :updatedAt)
            on conflict (auth_provider, auth_subject) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id,
                        @Param("authProvider") String authProvider,
                        @Param("authSubject") String authSubject,
                        @Param("email") String email,
                        @Param("displayName") String displayName,
                        @Param("fullName") String fullName,
                        @Param("createdAt") Instant createdAt,
                        @Param("updatedAt") Instant updatedAt);
}
