package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.port.JoinRequestRepository;
import com.lucchetta.core.port.LeagueInvitationRepository;
import com.lucchetta.core.port.LeagueMemberRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Adds a player to a league, whatever the way in (public join, invite code, accepted request or
 * accepted invitation; decision D-019). The caller holds the league lock.
 */
public class LeagueAdmission {

    private final LeagueMemberRepository memberRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final LeagueInvitationRepository invitationRepository;
    private final Clock clock;

    public LeagueAdmission(LeagueMemberRepository memberRepository,
                           JoinRequestRepository joinRequestRepository,
                           LeagueInvitationRepository invitationRepository,
                           Clock clock) {
        this.memberRepository = memberRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.invitationRepository = invitationRepository;
        this.clock = clock;
    }

    /**
     * Admits the player as a member, unless they already are one. Their other pending requests
     * and invitations to this league become moot and are cancelled.
     *
     * @throws ConflictException LEAGUE_FULL when the league already has the maximum of members
     */
    public LeagueMember admit(League league, UUID playerId) {
        LeagueAccess.requireNotArchived(league);
        Optional<LeagueMember> existing = memberRepository.find(league.id(), playerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        requireRoom(league);
        Instant now = clock.instant();
        LeagueMember member = new LeagueMember(league.id(), playerId, LeagueRole.MEMBER, now);
        memberRepository.insert(member);
        joinRequestRepository.cancelPending(league.id(), playerId, now);
        invitationRepository.cancelPending(league.id(), playerId, now);
        return member;
    }

    /** @throws ConflictException LEAGUE_FULL when no one else can join the league */
    public void requireRoom(League league) {
        if (memberRepository.count(league.id()) >= League.MAX_MEMBERS) {
            throw new ConflictException("LEAGUE_FULL");
        }
    }
}
