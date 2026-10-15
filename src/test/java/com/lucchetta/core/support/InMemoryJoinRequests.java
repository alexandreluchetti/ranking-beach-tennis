package com.lucchetta.core.support;

import com.lucchetta.core.domain.league.JoinRequest;
import com.lucchetta.core.domain.league.LeagueEntryStatus;
import com.lucchetta.core.port.JoinRequestRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryJoinRequests implements JoinRequestRepository {

    private final Map<UUID, JoinRequest> requests = new LinkedHashMap<>();

    public List<JoinRequest> all() {
        return List.copyOf(requests.values());
    }

    @Override
    public void insert(JoinRequest request) {
        if (findPending(request.leagueId(), request.playerId()).isPresent()) {
            throw new IllegalStateException("Already pending");
        }
        requests.put(request.id(), request);
    }

    @Override
    public Optional<JoinRequest> findById(UUID id) {
        return Optional.ofNullable(requests.get(id));
    }

    @Override
    public Optional<JoinRequest> findPending(UUID leagueId, UUID playerId) {
        return requests.values().stream()
                .filter(r -> r.pending() && r.leagueId().equals(leagueId) && r.playerId().equals(playerId))
                .findFirst();
    }

    @Override
    public List<JoinRequest> findPendingByLeague(UUID leagueId) {
        return requests.values().stream()
                .filter(r -> r.pending() && r.leagueId().equals(leagueId))
                .sorted(Comparator.comparing(JoinRequest::createdAt))
                .toList();
    }

    @Override
    public List<JoinRequest> findPendingByPlayer(UUID playerId) {
        return requests.values().stream().filter(r -> r.pending() && r.playerId().equals(playerId)).toList();
    }

    @Override
    public boolean decide(UUID id, LeagueEntryStatus status, UUID decidedBy, Instant decidedAt) {
        JoinRequest current = requests.get(id);
        if (current == null || !current.pending()) {
            return false;
        }
        requests.put(id, new JoinRequest(id, current.leagueId(), current.playerId(), status, current.createdAt(),
                decidedBy, decidedAt));
        return true;
    }

    @Override
    public void cancelPending(UUID leagueId, UUID playerId, Instant at) {
        findPending(leagueId, playerId).ifPresent(r -> decide(r.id(), LeagueEntryStatus.CANCELLED, null, at));
    }
}
