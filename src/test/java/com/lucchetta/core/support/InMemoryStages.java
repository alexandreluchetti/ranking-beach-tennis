package com.lucchetta.core.support;

import com.lucchetta.core.domain.tournament.TournamentStage;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.TournamentStageRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryStages implements TournamentStageRepository {

    private final Map<UUID, TournamentStage> stages = new LinkedHashMap<>();
    private final InMemoryMatches matches;

    /** @param matches to refuse deleting a stage that ever had a match, like the database */
    public InMemoryStages(InMemoryMatches matches) {
        this.matches = matches;
    }

    @Override
    public Optional<TournamentStage> findById(UUID id) {
        return Optional.ofNullable(stages.get(id));
    }

    @Override
    public Optional<TournamentStage> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public List<TournamentStage> findBySeason(UUID seasonId) {
        return stages.values().stream()
                .filter(s -> s.seasonId().equals(seasonId))
                .sorted(Comparator.comparing(TournamentStage::date))
                .toList();
    }

    @Override
    public boolean existsOn(UUID seasonId, LocalDate date) {
        return stages.values().stream().anyMatch(s -> s.seasonId().equals(seasonId) && s.date().equals(date));
    }

    @Override
    public TournamentStage insert(TournamentStage stage) {
        if (existsOn(stage.seasonId(), stage.date())) {
            throw new ConflictException("STAGE_ALREADY_EXISTS");
        }
        stages.put(stage.id(), stage);
        return stage;
    }

    @Override
    public TournamentStage update(TournamentStage stage) {
        TournamentStage current = stages.get(stage.id());
        if (current == null || current.version() != stage.version()) {
            throw new ConflictException("STAGE_MODIFIED");
        }
        TournamentStage saved = new TournamentStage(stage.id(), stage.leagueId(), stage.seasonId(), stage.date(),
                stage.format(), stage.entrants(), stage.pairs(), stage.fixtures(), stage.createdBy(),
                stage.createdAt(), stage.updatedAt(), stage.version() + 1);
        stages.put(saved.id(), saved);
        return saved;
    }

    @Override
    public void delete(UUID id) {
        if (!matches.findByStage(id).isEmpty()) {
            throw new RuleViolationException("STAGE_STARTED");
        }
        stages.remove(id);
    }
}
