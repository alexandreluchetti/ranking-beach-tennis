package com.lucchetta.core.support;

import com.lucchetta.core.domain.league.LeagueEntryStatus;
import com.lucchetta.core.domain.league.LeagueInvitation;
import com.lucchetta.core.port.LeagueInvitationRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryInvitations implements LeagueInvitationRepository {

    private final Map<UUID, LeagueInvitation> invitations = new LinkedHashMap<>();

    public List<LeagueInvitation> all() {
        return List.copyOf(invitations.values());
    }

    @Override
    public void insert(LeagueInvitation invitation) {
        if (findPending(invitation.leagueId(), invitation.playerId()).isPresent()) {
            throw new IllegalStateException("Already pending");
        }
        invitations.put(invitation.id(), invitation);
    }

    @Override
    public Optional<LeagueInvitation> findById(UUID id) {
        return Optional.ofNullable(invitations.get(id));
    }

    @Override
    public Optional<LeagueInvitation> findPending(UUID leagueId, UUID playerId) {
        return invitations.values().stream()
                .filter(i -> i.pending() && i.leagueId().equals(leagueId) && i.playerId().equals(playerId))
                .findFirst();
    }

    @Override
    public List<LeagueInvitation> findPendingByPlayer(UUID playerId) {
        return invitations.values().stream()
                .filter(i -> i.pending() && i.playerId().equals(playerId))
                .sorted(Comparator.comparing(LeagueInvitation::createdAt).reversed())
                .toList();
    }

    @Override
    public boolean decide(UUID id, LeagueEntryStatus status, Instant decidedAt) {
        LeagueInvitation current = invitations.get(id);
        if (current == null || !current.pending()) {
            return false;
        }
        invitations.put(id, new LeagueInvitation(id, current.leagueId(), current.playerId(), current.invitedBy(),
                status, current.createdAt(), decidedAt));
        return true;
    }

    @Override
    public void cancelPending(UUID leagueId, UUID playerId, Instant at) {
        findPending(leagueId, playerId).ifPresent(i -> decide(i.id(), LeagueEntryStatus.CANCELLED, at));
    }
}
