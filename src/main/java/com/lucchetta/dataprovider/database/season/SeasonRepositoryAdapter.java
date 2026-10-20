package com.lucchetta.dataprovider.database.season;

import com.lucchetta.core.domain.Season;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.port.SeasonRepository;
import com.lucchetta.core.domain.PastPlayingDays;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class SeasonRepositoryAdapter implements SeasonRepository {

    private static final String OVERLAP_CONSTRAINT = "ex_season_no_overlap";

    private final SeasonJpaRepository jpaRepository;
    private final JdbcClient jdbcClient;

    public SeasonRepositoryAdapter(SeasonJpaRepository jpaRepository, JdbcClient jdbcClient) {
        this.jpaRepository = jpaRepository;
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Season> findAll(UUID leagueId) {
        return toDomain(jpaRepository.findAllByLeagueIdOrderByStartDateDesc(leagueId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Season> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Season> findContaining(UUID leagueId, LocalDate date) {
        return jpaRepository.findContaining(leagueId, date).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsOverlapping(UUID leagueId, LocalDate startDate, LocalDate endDate, UUID excludedId) {
        return excludedId == null
                ? jpaRepository.existsOverlapping(leagueId, startDate, endDate)
                : jpaRepository.existsOverlappingExcluding(leagueId, startDate, endDate, excludedId);
    }

    @Override
    @Transactional
    public Season insert(Season season) {
        SeasonEntity entity = new SeasonEntity(season.id(), season.leagueId(), season.name(), season.startDate(),
                season.endDate(), toColumn(season.playingDays()), season.createdBy(), season.createdAt(),
                season.updatedAt());
        SeasonEntity saved = saveTranslatingOverlap(entity);
        savePastPlayingDays(season);
        return toDomain(saved);
    }

    @Override
    @Transactional
    public Season update(Season season) {
        SeasonEntity entity = jpaRepository.findById(season.id())
                .orElseThrow(() -> new IllegalStateException("Season not found: " + season.id()));
        entity.apply(season.name(), season.startDate(), season.endDate(), toColumn(season.playingDays()),
                season.updatedAt());
        SeasonEntity saved = saveTranslatingOverlap(entity);
        savePastPlayingDays(season);
        return toDomain(saved);
    }

    /** The exclusion constraint is the final guard against concurrent overlapping writes. */
    private SeasonEntity saveTranslatingOverlap(SeasonEntity entity) {
        try {
            return jpaRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(OVERLAP_CONSTRAINT)) {
                throw new ConflictException("SEASON_OVERLAP");
            }
            throw e;
        }
    }

    private void savePastPlayingDays(Season season) {
        jdbcClient.sql("delete from season_past_playing_days where season_id = :seasonId")
                .param("seasonId", season.id())
                .update();
        for (PastPlayingDays past : season.pastPlayingDays()) {
            jdbcClient.sql("""
                            insert into season_past_playing_days (season_id, last_day, playing_days)
                            values (:seasonId, :lastDay, cast(:playingDays as smallint[]))
                            """)
                    .param("seasonId", season.id())
                    .param("lastDay", past.lastDay())
                    .param("playingDays", arrayLiteral(past.playingDays()))
                    .update();
        }
    }

    /** Seasons with their past playing days, in one query for all of them. */
    private List<Season> toDomain(List<SeasonEntity> entities) {
        Map<UUID, List<PastPlayingDays>> past = pastPlayingDays(entities.stream().map(SeasonEntity::getId).toList());
        return entities.stream().map(entity -> toDomain(entity, past.getOrDefault(entity.getId(), List.of()))).toList();
    }

    private Season toDomain(SeasonEntity entity) {
        return toDomain(entity, pastPlayingDays(List.of(entity.getId())).getOrDefault(entity.getId(), List.of()));
    }

    private Map<UUID, List<PastPlayingDays>> pastPlayingDays(Collection<UUID> seasonIds) {
        if (seasonIds.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql("""
                        select season_id, last_day, array_to_string(playing_days, ',') as playing_days
                        from season_past_playing_days where season_id in (:seasonIds) order by last_day
                        """)
                .param("seasonIds", seasonIds)
                .query((rs, rowNum) -> Map.entry(rs.getObject("season_id", UUID.class), new PastPlayingDays(
                        rs.getObject("last_day", LocalDate.class), fromText(rs.getString("playing_days")))))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private static Season toDomain(SeasonEntity entity, List<PastPlayingDays> past) {
        return new Season(entity.getId(), entity.getLeagueId(), entity.getName(), entity.getStartDate(),
                entity.getEndDate(), fromColumn(entity.getPlayingDays()), past, entity.getCreatedBy(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static Short[] toColumn(Set<DayOfWeek> days) {
        return days.stream().sorted().map(day -> (short) day.getValue()).toArray(Short[]::new);
    }

    private static String arrayLiteral(Set<DayOfWeek> days) {
        return Arrays.stream(toColumn(days)).map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

    private static Set<DayOfWeek> fromText(String days) {
        return Arrays.stream(days.split(",")).map(day -> DayOfWeek.of(Integer.parseInt(day)))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    private static Set<DayOfWeek> fromColumn(Short[] days) {
        return Arrays.stream(days).map(DayOfWeek::of)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }
}
