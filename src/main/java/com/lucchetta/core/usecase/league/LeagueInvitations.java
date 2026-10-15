package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueEntryStatus;
import com.lucchetta.core.domain.league.LeagueInvitation;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.LeagueInvitationRepository;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.LeagueRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.usecase.notification.LeagueNotifier;
import com.lucchetta.core.usecase.notification.PushDispatcher;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-app invitations (decision D-019). A league administrator types the invitee's exact e-mail;
 * the answer never reveals whether an account has it. The invitee accepts or rejects it from the
 * notification.
 */
public class LeagueInvitations {

    static final int EMAIL_MAX_LENGTH = 320;

    /** A pending invitation as the invitee sees it. */
    public record ReceivedInvitation(LeagueInvitation invitation, League league, int memberCount,
                                     String invitedByName) {
    }

    private final LeagueAccess leagueAccess;
    private final LeagueInvitationRepository invitationRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueMemberRepository memberRepository;
    private final PlayerRepository playerRepository;
    private final LeagueAdmission admission;
    private final LeagueNotifier notifier;
    private final PushDispatcher pushDispatcher;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public LeagueInvitations(LeagueAccess leagueAccess,
                             LeagueInvitationRepository invitationRepository,
                             LeagueRepository leagueRepository,
                             LeagueMemberRepository memberRepository,
                             PlayerRepository playerRepository,
                             LeagueAdmission admission,
                             LeagueNotifier notifier,
                             PushDispatcher pushDispatcher,
                             TransactionRunner transactionRunner,
                             Clock clock) {
        this.leagueAccess = leagueAccess;
        this.invitationRepository = invitationRepository;
        this.leagueRepository = leagueRepository;
        this.memberRepository = memberRepository;
        this.playerRepository = playerRepository;
        this.admission = admission;
        this.notifier = notifier;
        this.pushDispatcher = pushDispatcher;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    /**
     * Invites the accounts with this exact e-mail. Members and users already invited are skipped
     * silently, and nothing tells the caller whether such an account exists.
     */
    public void invite(AuthenticatedUser user, UUID leagueId, String email) {
        String normalizedEmail = normalizeEmail(email);
        List<Notification> notifications = transactionRunner.inTransaction(() -> {
            LeagueAccess.Context context = leagueAccess.lockedContext(user, leagueId);
            LeagueAccess.requireAdministrator(context);
            League league = context.league();
            LeagueAccess.requireNotArchived(league);
            admission.requireRoom(league);
            List<Notification> created = new ArrayList<>();
            for (Player invitee : playerRepository.findByEmail(normalizedEmail)) {
                if (memberRepository.find(leagueId, invitee.id()).isPresent()
                        || invitationRepository.findPending(leagueId, invitee.id()).isPresent()) {
                    continue;
                }
                LeagueInvitation invitation = LeagueInvitation.pending(leagueId, invitee.id(), context.player().id(),
                        clock.instant());
                invitationRepository.insert(invitation);
                created.addAll(notifier.invitationReceived(invitation, league,
                        context.player().displayName().value()));
            }
            return created;
        });
        pushDispatcher.dispatch(notifications, null);
    }

    /** The caller's pending invitations, most recent first. */
    public List<ReceivedInvitation> received(AuthenticatedUser user) {
        Player player = leagueAccess.player(user);
        List<LeagueInvitation> invitations = invitationRepository.findPendingByPlayer(player.id());
        Map<UUID, League> leagues = leagueRepository.findAllByIds(invitations.stream()
                        .map(LeagueInvitation::leagueId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(League::id, Function.identity()));
        Map<UUID, Integer> counts = memberRepository.countByLeagues(leagues.keySet());
        Map<UUID, String> inviters = playerRepository.findAllByIds(invitations.stream()
                        .map(LeagueInvitation::invitedBy).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Player::id, inviter -> inviter.displayName().value()));
        return invitations.stream()
                .map(invitation -> new ReceivedInvitation(invitation, leagues.get(invitation.leagueId()),
                        counts.getOrDefault(invitation.leagueId(), 0), inviters.get(invitation.invitedBy())))
                .toList();
    }

    /** The invitee accepts and becomes a member (if the league is not full). */
    public LeagueView accept(AuthenticatedUser user, UUID invitationId) {
        Player player = leagueAccess.player(user);
        LeagueInvitation invitation = findOwn(invitationId, player);
        return transactionRunner.inTransaction(() -> {
            LeagueAccess.Context context = leagueAccess.lockedContext(user, invitation.leagueId());
            LeagueAccess.requireNotArchived(context.league());
            decide(invitation, LeagueEntryStatus.ACCEPTED);
            LeagueMember member = admission.admit(context.league(), player.id());
            return leagueAccess.view(new LeagueAccess.Context(user, player, context.league(), member));
        });
    }

    public void reject(AuthenticatedUser user, UUID invitationId) {
        decide(findOwn(invitationId, leagueAccess.player(user)), LeagueEntryStatus.REJECTED);
    }

    private LeagueInvitation findOwn(UUID invitationId, Player player) {
        return invitationRepository.findById(invitationId)
                .filter(invitation -> invitation.playerId().equals(player.id()))
                .orElseThrow(() -> new NotFoundException("INVITATION_NOT_FOUND"));
    }

    private void decide(LeagueInvitation invitation, LeagueEntryStatus status) {
        if (!invitation.pending() || !invitationRepository.decide(invitation.id(), status, clock.instant())) {
            throw new ConflictException("INVITATION_NOT_PENDING");
        }
    }

    private static String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.strip();
        int at = normalized.indexOf('@');
        if (at <= 0 || at == normalized.length() - 1 || normalized.length() > EMAIL_MAX_LENGTH
                || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new RuleViolationException("INVALID_EMAIL");
        }
        return normalized;
    }
}
