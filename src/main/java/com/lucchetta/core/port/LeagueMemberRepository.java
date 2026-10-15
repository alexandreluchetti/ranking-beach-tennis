package com.lucchetta.core.port;

import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface LeagueMemberRepository {

    Optional<LeagueMember> find(UUID leagueId, UUID playerId);

    List<LeagueMember> findByLeague(UUID leagueId);

    /** Memberships of the player, in any league. */
    List<LeagueMember> findByPlayer(UUID playerId);

    int count(UUID leagueId);

    /** Member count per league; leagues without members are absent. */
    Map<UUID, Integer> countByLeagues(Collection<UUID> leagueIds);

    int countAdmins(UUID leagueId);

    void insert(LeagueMember member);

    void updateRole(UUID leagueId, UUID playerId, LeagueRole role);

    void delete(UUID leagueId, UUID playerId);
}
