package com.lucchetta.core.port;

import com.lucchetta.core.domain.league.League;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeagueRepository {

    Optional<League> findById(UUID id);

    /**
     * Reads the league and locks it until the end of the current transaction, serializing
     * membership changes (member limit, last administrator).
     */
    Optional<League> findByIdForUpdate(UUID id);

    List<League> findAllByIds(Collection<UUID> ids);

    /** Whether another league ({@code excludedId} aside) has this name, ignoring case. */
    boolean existsByName(String name, UUID excludedId);

    /** Non-archived leagues created by the player. */
    int countActiveCreatedBy(UUID playerId);

    /**
     * Leagues whose name contains {@code nameFragment} (ignoring case; null or blank matches all),
     * archived or not, ordered by name.
     */
    List<League> search(String nameFragment, boolean archived, int offset, int limit);

    /** @throws com.lucchetta.core.exception.ConflictException LEAGUE_NAME_TAKEN */
    League insert(League league);

    /**
     * Saves a changed league if it is still at {@code league.version()}.
     *
     * @throws com.lucchetta.core.exception.ConflictException LEAGUE_NAME_TAKEN or LEAGUE_MODIFIED
     */
    League update(League league);
}
