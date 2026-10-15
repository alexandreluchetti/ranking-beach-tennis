package com.lucchetta.dataprovider.database.season;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "season")
public class SeasonEntity {

    @Id
    private UUID id;

    @Column(name = "league_id", nullable = false, updatable = false)
    private UUID leagueId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SeasonEntity() {
    }

    public SeasonEntity(UUID id, UUID leagueId, String name, LocalDate startDate, LocalDate endDate,
                        UUID createdBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.leagueId = leagueId;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void apply(String name, LocalDate startDate, LocalDate endDate, Instant updatedAt) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeagueId() {
        return leagueId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
