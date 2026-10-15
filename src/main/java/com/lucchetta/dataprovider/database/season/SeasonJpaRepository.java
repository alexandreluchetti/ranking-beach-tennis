package com.lucchetta.dataprovider.database.season;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SeasonJpaRepository extends JpaRepository<SeasonEntity, UUID> {

    List<SeasonEntity> findAllByLeagueIdOrderByStartDateDesc(UUID leagueId);

    @Query("""
            select s from SeasonEntity s
            where s.leagueId = :leagueId and s.startDate <= :date and s.endDate >= :date
            """)
    Optional<SeasonEntity> findContaining(@Param("leagueId") UUID leagueId, @Param("date") LocalDate date);

    @Query("""
            select count(s) > 0 from SeasonEntity s
            where s.leagueId = :leagueId and s.startDate <= :endDate and s.endDate >= :startDate
            """)
    boolean existsOverlapping(@Param("leagueId") UUID leagueId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);

    @Query("""
            select count(s) > 0 from SeasonEntity s
            where s.leagueId = :leagueId and s.startDate <= :endDate and s.endDate >= :startDate
              and s.id <> :excludedId
            """)
    boolean existsOverlappingExcluding(@Param("leagueId") UUID leagueId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       @Param("excludedId") UUID excludedId);
}
