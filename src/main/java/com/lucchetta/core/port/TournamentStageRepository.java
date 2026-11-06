package com.lucchetta.core.port;

import com.lucchetta.core.domain.tournament.TournamentStage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentStageRepository {

    Optional<TournamentStage> findById(UUID id);

    /** Same as {@link #findById}, locking the stage until the end of the current transaction. */
    Optional<TournamentStage> findByIdForUpdate(UUID id);

    /** Stages of a season, by date. */
    List<TournamentStage> findBySeason(UUID seasonId);

    boolean existsOn(UUID seasonId, LocalDate date);

    /**
     * @throws com.lucchetta.core.exception.ConflictException STAGE_ALREADY_EXISTS when the
     *                                                       season already has a stage that day
     */
    TournamentStage insert(TournamentStage stage);

    /**
     * Saves a changed stage if it is still at {@code stage.version()}.
     *
     * @return the stored stage, with its version incremented
     * @throws com.lucchetta.core.exception.ConflictException STAGE_MODIFIED when someone else
     *                                                       changed it first
     */
    TournamentStage update(TournamentStage stage);

    /**
     * Deletes a stage that never had a score.
     *
     * @throws com.lucchetta.core.exception.RuleViolationException STAGE_STARTED when a match was
     *                                                            ever entered for it
     */
    void delete(UUID id);
}
