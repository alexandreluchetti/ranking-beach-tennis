package com.lucchetta.core.support;

import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.LeagueRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Leagues and their members, in memory. */
public class InMemoryLeagues implements LeagueRepository, LeagueMemberRepository {

    private final Map<UUID, League> leagues = new LinkedHashMap<>();
    private final List<LeagueMember> members = new ArrayList<>();

    public League add(League league, LeagueMember... initialMembers) {
        leagues.put(league.id(), league);
        members.addAll(List.of(initialMembers));
        return league;
    }

    @Override
    public Optional<League> findById(UUID id) {
        return Optional.ofNullable(leagues.get(id));
    }

    @Override
    public Optional<League> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public List<League> findAllByIds(Collection<UUID> ids) {
        return ids.stream().map(leagues::get).filter(league -> league != null).toList();
    }

    @Override
    public boolean existsByName(String name, UUID excludedId) {
        return leagues.values().stream()
                .anyMatch(league -> league.name().equalsIgnoreCase(name) && !league.id().equals(excludedId));
    }

    @Override
    public int countActiveCreatedBy(UUID playerId) {
        return (int) leagues.values().stream()
                .filter(league -> playerId.equals(league.createdBy()) && !league.archived())
                .count();
    }

    @Override
    public List<League> search(String nameFragment, boolean archived, int offset, int limit) {
        String fragment = nameFragment == null ? "" : nameFragment.strip().toLowerCase();
        return leagues.values().stream()
                .filter(league -> league.archived() == archived)
                .filter(league -> league.name().toLowerCase().contains(fragment))
                .sorted(Comparator.comparing(league -> league.name().toLowerCase()))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public League insert(League league) {
        if (existsByName(league.name(), league.id())) {
            throw new ConflictException("LEAGUE_NAME_TAKEN");
        }
        leagues.put(league.id(), league);
        return league;
    }

    @Override
    public League update(League league) {
        League current = leagues.get(league.id());
        if (current.version() != league.version()) {
            throw new ConflictException("LEAGUE_MODIFIED");
        }
        League saved = new League(league.id(), league.name(), league.description(), league.visibility(),
                league.inviteCode(), league.createdBy(), league.createdAt(), league.updatedAt(), league.archivedAt(),
                league.archivedBy(), league.version() + 1);
        leagues.put(saved.id(), saved);
        return saved;
    }

    @Override
    public Optional<LeagueMember> find(UUID leagueId, UUID playerId) {
        return members.stream()
                .filter(m -> m.leagueId().equals(leagueId) && m.playerId().equals(playerId))
                .findFirst();
    }

    @Override
    public List<LeagueMember> findByLeague(UUID leagueId) {
        return members.stream().filter(m -> m.leagueId().equals(leagueId)).toList();
    }

    @Override
    public List<LeagueMember> findByPlayer(UUID playerId) {
        return members.stream().filter(m -> m.playerId().equals(playerId)).toList();
    }

    @Override
    public int count(UUID leagueId) {
        return findByLeague(leagueId).size();
    }

    @Override
    public Map<UUID, Integer> countByLeagues(Collection<UUID> leagueIds) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (UUID id : leagueIds) {
            int count = count(id);
            if (count > 0) {
                counts.put(id, count);
            }
        }
        return counts;
    }

    @Override
    public int countAdmins(UUID leagueId) {
        return (int) findByLeague(leagueId).stream().filter(LeagueMember::admin).count();
    }

    @Override
    public void insert(LeagueMember member) {
        if (find(member.leagueId(), member.playerId()).isPresent()) {
            throw new IllegalStateException("Already a member");
        }
        members.add(member);
    }

    @Override
    public void updateRole(UUID leagueId, UUID playerId, LeagueRole role) {
        LeagueMember current = find(leagueId, playerId).orElseThrow();
        members.set(members.indexOf(current), current.withRole(role));
    }

    @Override
    public void delete(UUID leagueId, UUID playerId) {
        find(leagueId, playerId).ifPresent(members::remove);
    }
}
