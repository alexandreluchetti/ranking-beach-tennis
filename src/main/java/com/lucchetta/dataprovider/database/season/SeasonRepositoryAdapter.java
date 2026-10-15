package com.lucchetta.dataprovider.database.season;

import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.league.DefaultLeague;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.port.SeasonRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SeasonRepositoryAdapter implements SeasonRepository {

    private static final String OVERLAP_CONSTRAINT = "ex_season_no_overlap";

    private final SeasonJpaRepository jpaRepository;

    public SeasonRepositoryAdapter(SeasonJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Season> findAll() {
        return jpaRepository.findAllByOrderByStartDateDesc().stream()
                .map(SeasonRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Season> findById(UUID id) {
        return jpaRepository.findById(id).map(SeasonRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Season> findContaining(LocalDate date) {
        return jpaRepository.findContaining(date).map(SeasonRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsOverlapping(LocalDate startDate, LocalDate endDate, UUID excludedId) {
        return excludedId == null
                ? jpaRepository.existsOverlapping(startDate, endDate)
                : jpaRepository.existsOverlappingExcluding(startDate, endDate, excludedId);
    }

    @Override
    @Transactional
    public Season insert(Season season) {
        SeasonEntity entity = new SeasonEntity(season.id(), DefaultLeague.ID, season.name(), season.startDate(),
                season.endDate(), season.createdBy(), season.createdAt(), season.updatedAt());
        return toDomain(saveTranslatingOverlap(entity));
    }

    @Override
    @Transactional
    public Season update(Season season) {
        SeasonEntity entity = jpaRepository.findById(season.id())
                .orElseThrow(() -> new IllegalStateException("Season not found: " + season.id()));
        entity.apply(season.name(), season.startDate(), season.endDate(), season.updatedAt());
        return toDomain(saveTranslatingOverlap(entity));
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

    private static Season toDomain(SeasonEntity entity) {
        return new Season(entity.getId(), entity.getName(), entity.getStartDate(), entity.getEndDate(),
                entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
