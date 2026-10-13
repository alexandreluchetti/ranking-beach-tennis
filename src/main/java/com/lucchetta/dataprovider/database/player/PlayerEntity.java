package com.lucchetta.dataprovider.database.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class PlayerEntity {

    @Id
    private UUID id;

    @Column(name = "auth_provider", nullable = false)
    private String authProvider;

    @Column(name = "auth_subject", nullable = false)
    private String authSubject;

    @Column(name = "email")
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlayerEntity() {
    }

    public PlayerEntity(UUID id, String authProvider, String authSubject, String email,
                        String displayName, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.authProvider = authProvider;
        this.authSubject = authSubject;
        this.email = email;
        this.displayName = displayName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public String getAuthSubject() {
        return authSubject;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
