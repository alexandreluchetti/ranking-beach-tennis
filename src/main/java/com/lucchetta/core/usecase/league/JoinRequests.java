package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.league.JoinRequest;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueEntryStatus;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueVisibility;
import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.JoinRequestRepository;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.usecase.notification.LeagueNotifier;
import com.lucchetta.core.usecase.notification.PushDispatcher;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Requests to join a private league (decision D-019): any league administrator accepts or
 * rejects; the requester may cancel. At most one pending request per user and league.
 */
public class JoinRequests {

    public record PendingRequest(JoinRequest request, String displayName) {
    }

    private record Outcome<T>(T value, List<Notification> notifications) {
    }

    private final LeagueAccess leagueAccess;
    private final JoinRequestRepository joinRequestRepository;
    private final LeagueMemberRepository memberRepository;
    private final PlayerRepository playerRepository;
    private final LeagueAdmission admission;
    private final LeagueNotifier notifier;
    private final PushDispatcher pushDispatcher;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public JoinRequests(LeagueAccess leagueAccess,
                        JoinRequestRepository joinRequestRepository,
                        LeagueMemberRepository memberRepository,
                        PlayerRepository playerRepository,
                        LeagueAdmission admission,
                        LeagueNotifier notifier,
                        PushDispatcher pushDispatcher,
                        TransactionRunner transactionRunner,
                        Clock clock) {
        this.leagueAccess = leagueAccess;
        this.joinRequestRepository = joinRequestRepository;
        this.memberRepository = memberRepository;
        this.playerRepository = playerRepository;
        this.admission = admission;
        this.notifier = notifier;
        this.pushDispatcher = pushDispatcher;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    /** Asks to join a private league. Asking again while pending changes nothing. */
    public LeagueView request(AuthenticatedUser user, UUID leagueId) {
        Outcome<LeagueView> outcome = transactionRunner.inTransaction(() -> {
            LeagueAccess.Context context = leagueAccess.lockedContext(user, leagueId);
            League league = context.league();
            if (context.member()) {
                throw new ConflictException("ALREADY_LEAGUE_MEMBER");
            }
            LeagueAccess.requireNotArchived(league);
            if (league.visibility() == LeagueVisibility.PUBLIC) {
                throw new RuleViolationException("LEAGUE_PUBLIC");
            }
            Player requester = context.player();
            if (joinRequestRepository.findPending(leagueId, requester.id()).isPresent()) {
                return new Outcome<>(leagueAccess.view(context), List.of());
            }
            admission.requireRoom(league);
            JoinRequest request = JoinRequest.pending(leagueId, requester.id(), clock.instant());
            joinRequestRepository.insert(request);
            List<UUID> administrators = memberRepository.findByLeague(leagueId).stream()
                    .filter(LeagueMember::admin)
                    .map(LeagueMember::playerId)
                    .toList();
            List<Notification> notifications = notifier.joinRequested(request, league,
                    requester.displayName().value(), administrators);
            return new Outcome<>(leagueAccess.view(context), notifications);
        });
        pushDispatcher.dispatch(outcome.notifications(), null);
        return outcome.value();
    }

    /** Withdraws the caller's pending request. */
    public void cancel(AuthenticatedUser user, UUID leagueId) {
        Player player = leagueAccess.player(user);
        JoinRequest request = joinRequestRepository.findPending(leagueId, player.id())
                .orElseThrow(() -> new NotFoundException("JOIN_REQUEST_NOT_FOUND"));
        if (!joinRequestRepository.decide(request.id(), LeagueEntryStatus.CANCELLED, null, clock.instant())) {
            throw new ConflictException("JOIN_REQUEST_NOT_PENDING");
        }
    }

    /** Pending requests of the league, oldest first (league administrators). */
    public List<PendingRequest> pending(AuthenticatedUser user, UUID leagueId) {
        LeagueAccess.requireAdministrator(leagueAccess.context(user, leagueId));
        List<JoinRequest> requests = joinRequestRepository.findPendingByLeague(leagueId);
        Map<UUID, Player> players = playerRepository.findAllByIds(requests.stream().map(JoinRequest::playerId)
                        .toList()).stream()
                .collect(Collectors.toMap(Player::id, Function.identity()));
        return requests.stream()
                .map(request -> new PendingRequest(request, players.get(request.playerId()).displayName().value()))
                .toList();
    }

    /** A league administrator accepts: the requester becomes a member (if the league is not full). */
    public void accept(AuthenticatedUser user, UUID leagueId, UUID requestId) {
        answer(user, leagueId, requestId, true);
    }

    public void reject(AuthenticatedUser user, UUID leagueId, UUID requestId) {
        answer(user, leagueId, requestId, false);
    }

    private void answer(AuthenticatedUser user, UUID leagueId, UUID requestId, boolean accepted) {
        Outcome<Void> outcome = transactionRunner.inTransaction(() -> {
            LeagueAccess.Context context = leagueAccess.lockedContext(user, leagueId);
            LeagueAccess.requireAdministrator(context);
            League league = context.league();
            LeagueAccess.requireNotArchived(league);
            JoinRequest request = joinRequestRepository.findById(requestId)
                    .filter(found -> found.leagueId().equals(leagueId))
                    .orElseThrow(() -> new NotFoundException("JOIN_REQUEST_NOT_FOUND"));
            Instant now = clock.instant();
            LeagueEntryStatus status = accepted ? LeagueEntryStatus.ACCEPTED : LeagueEntryStatus.REJECTED;
            if (!request.pending() || !joinRequestRepository.decide(requestId, status, context.player().id(), now)) {
                throw new ConflictException("JOIN_REQUEST_NOT_PENDING");
            }
            if (accepted) {
                admission.admit(league, request.playerId());
            }
            return new Outcome<Void>(null, notifier.joinRequestAnswered(request, league, accepted));
        });
        pushDispatcher.dispatch(outcome.notifications(), null);
    }
}
