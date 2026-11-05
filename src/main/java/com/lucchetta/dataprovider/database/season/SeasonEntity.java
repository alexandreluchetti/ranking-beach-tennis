package com.lucchetta.dataprovider.database.season;

import com.lucchetta.core.domain.RankingType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /** ISO days of the week (1 = Monday), sorted. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "playing_days", nullable = false)
    private Short[] playingDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "ranking_type", nullable = false)
    private RankingType rankingType;

    /** Champion, runner-up, semifinal, quarterfinal, group stage; daily tournaments only. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "placement_points")
    private Integer[] placementPoints;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SeasonEntity() {
    }

    public SeasonEntity(UUID id, UUID leagueId, String name, LocalDate startDate, LocalDate endDate,
                        Short[] playingDays, RankingType rankingType, Integer[] placementPoints, UUID createdBy,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.leagueId = leagueId;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.playingDays = playingDays;
        this.rankingType = rankingType;
        this.placementPoints = placementPoints;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void apply(String name, LocalDate startDate, LocalDate endDate, Short[] playingDays,
                      RankingType rankingType, Integer[] placementPoints, Instant updatedAt) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.playingDays = playingDays;
        this.rankingType = rankingType;
        this.placementPoints = placementPoints;
        this.updatedAt = updatedAt;
    }

    public RankingType getRankingType() {
        return rankingType;
    }

    public Integer[] getPlacementPoints() {
        return placementPoints;
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

    public Short[] getPlayingDays() {
        return playingDays;
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
