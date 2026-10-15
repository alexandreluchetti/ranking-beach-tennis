package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.domain.league.LeagueVisibility;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.port.TransactionRunner;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Members of a league (decisions D-017 to D-019): joining a public league, leaving, removal,
 * promotion and revocation of league administrators. Every change locks the league, so the
 * member limit and the "at least one administrator" rule hold under concurrent requests.
 */
public class LeagueMembership {

    private final LeagueAccess leagueAccess;
    private final LeagueMemberRepository memberRepository;
    private final PlayerRepository playerRepository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public LeagueMembership(LeagueAccess leagueAccess,
                            LeagueMemberRepository memberRepository,
                            PlayerRepository playerRepository,
                            TransactionRunner transactionRunner,
                            Clock clock) {
        this.leagueAccess = leagueAccess;
        this.memberRepository = memberRepository;
        this.playerRepository = playerRepository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    /** Joins a public league. Joining a league one is already a member of changes nothing. */
    public LeagueView join(AuthenticatedUser user, UUID leagueId) {
        return transactionRunner.inTransaction(() -> {
            LeagueAccess.Context context = leagueAccess.lockedContext(user, leagueId);
            if (context.member()) {
                return leagueAccess.view(context);
            }
            League league = context.league();
            LeagueAccess.requireNotArchived(league);
            if (league.visibility() != LeagueVisibility.PUBLIC) {
                throw new RuleViolationException("LEAGUE_PRIVATE");
            }
            LeagueMember member = addMember(league, context.player().id());
            return leagueAccess.view(new LeagueAccess.Context(user, context.player(), league, member));
        });
    }

    public void leave(AuthenticatedUser user, UUID leagueId) {
        transactionRunner.inTransaction(() -> {
            LeagueAccess.Context context = leagueAccess.lockedContext(user, leagueId);
            if (!context.member()) {
                throw new NotFoundException("LEAGUE_MEMBER_NOT_FOUND");
            }
            requireAnotherAdministrator(context.membership());
            memberRepository.delete(leagueId, context.player().id());
            return null;
        });
    }

    /** A league administrator removes a member; their matches stay valid. */
    public void remove(AuthenticatedUser user, UUID leagueId, UUID playerId) {
        transactionRunner.inTransaction(() -> {
            LeagueAccess.Context context = leagueAccess.lockedContext(user, leagueId);
            LeagueAccess.requireAdministrator(context);
            LeagueAccess.requireNotArchived(context.league());
            LeagueMember target = requireMember(leagueId, playerId);
            requireAnotherAdministrator(target);
            memberRepository.delete(leagueId, playerId);
            return null;
        });
    }

    /**
     * Promotes a member to league administrator or revokes it. Administrators may revoke
     * themselves unless they are the last one.
     */
    public MemberView changeRole(AuthenticatedUser user, UUID leagueId, UUID playerId, LeagueRole role) {
        if (role == null) {
            throw new RuleViolationException("INVALID_LEAGUE_ROLE");
        }
        return transactionRunner.inTransaction(() -> {
            LeagueAccess.Context context = leagueAccess.lockedContext(user, leagueId);
            LeagueAccess.requireAdministrator(context);
            LeagueAccess.requireNotArchived(context.league());
            LeagueMember target = requireMember(leagueId, playerId);
            if (target.role() != role) {
                if (role == LeagueRole.MEMBER) {
                    requireAnotherAdministrator(target);
                }
                memberRepository.updateRole(leagueId, playerId, role);
            }
            return withName(target.withRole(role));
        });
    }

    /** Members of the league, administrators first, then by name. Members only. */
    public List<MemberView> members(AuthenticatedUser user, UUID leagueId) {
        leagueAccess.requireMember(user, leagueId);
        List<LeagueMember> members = memberRepository.findByLeague(leagueId);
        Map<UUID, String> names = names(members);
        return members.stream()
                .map(member -> new MemberView(member, names.get(member.playerId())))
                .sorted(Comparator.comparing((MemberView view) -> view.member().role())
                        .thenComparing(view -> view.displayName().toLowerCase()))
                .toList();
    }

    /**
     * Adds a player to a league locked by the caller's transaction. Shared by every way of
     * joining (decision D-019).
     *
     * @throws ConflictException LEAGUE_FULL when the league already has the maximum of members
     */
    LeagueMember addMember(League league, UUID playerId) {
        if (memberRepository.count(league.id()) >= League.MAX_MEMBERS) {
            throw new ConflictException("LEAGUE_FULL");
        }
        LeagueMember member = new LeagueMember(league.id(), playerId, LeagueRole.MEMBER, clock.instant());
        memberRepository.insert(member);
        return member;
    }

    private LeagueMember requireMember(UUID leagueId, UUID playerId) {
        return memberRepository.find(leagueId, playerId)
                .orElseThrow(() -> new NotFoundException("LEAGUE_MEMBER_NOT_FOUND"));
    }

    /** A league always keeps at least one administrator (decision D-018). */
    private void requireAnotherAdministrator(LeagueMember member) {
        if (member.admin() && memberRepository.countAdmins(member.leagueId()) <= 1) {
            throw new RuleViolationException("LAST_LEAGUE_ADMIN");
        }
    }

    private MemberView withName(LeagueMember member) {
        return new MemberView(member, names(List.of(member)).get(member.playerId()));
    }

    private Map<UUID, String> names(List<LeagueMember> members) {
        return playerRepository.findAllByIds(members.stream().map(LeagueMember::playerId).toList()).stream()
                .collect(Collectors.toMap(Player::id, player -> player.displayName().value(), (a, b) -> a));
    }
}
