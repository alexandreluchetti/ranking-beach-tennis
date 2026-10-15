package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.league.JoinRequest;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueEntryStatus;
import com.lucchetta.core.domain.league.LeagueInvitation;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.domain.notification.Notification;
import com.lucchetta.core.domain.notification.NotificationType;
import com.lucchetta.core.exception.BusinessException;
import com.lucchetta.core.exception.ForbiddenException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.stream.IntStream;

import static com.lucchetta.core.domain.league.LeagueVisibility.PRIVATE;
import static com.lucchetta.core.domain.league.LeagueVisibility.PUBLIC;
import static com.lucchetta.core.usecase.league.LeagueFixture.NOW;
import static com.lucchetta.core.usecase.league.LeagueFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Join requests, in-app invitations and invite codes (decision D-019). */
class LeagueEntryTest {

    private final LeagueFixture fixture = new LeagueFixture();
    private final Player ana = fixture.player("Ana");
    private final Player bruno = fixture.player("Bruno");
    private final Player carla = fixture.player("Carla");
    private final Player diego = fixture.player("Diego");
    private final League privateLeague = fixture.league("Liga Fechada", PRIVATE, ana, bruno);

    @Test
    void joinRequestNotifiesEveryLeagueAdministratorOnce() {
        fixture.membership.changeRole(user(ana), privateLeague.id(), bruno.id(), LeagueRole.ADMIN);

        LeagueView view = fixture.requests.request(user(carla), privateLeague.id());
        fixture.requests.request(user(carla), privateLeague.id());

        assertThat(view.joinRequestPending()).isTrue();
        assertThat(view.myRole()).isNull();
        assertThat(fixture.joinRequests.all()).hasSize(1);
        JoinRequest request = fixture.joinRequests.all().getFirst();
        assertThat(fixture.notifications.all())
                .extracting(Notification::recipientId)
                .containsExactlyInAnyOrder(ana.id(), bruno.id());
        Notification toAna = fixture.notifications.of(ana.id()).getFirst();
        assertThat(toAna.type()).isEqualTo(NotificationType.LEAGUE_JOIN_REQUESTED);
        assertThat(toAna.title()).isEqualTo("Pedido de entrada");
        assertThat(toAna.body()).isEqualTo("Carla pediu para entrar na liga Liga Fechada.");
        assertThat(toAna.leagueId()).isEqualTo(privateLeague.id());
        assertThat(toAna.joinRequestId()).isEqualTo(request.id());
        assertThat(fixture.queryLeagues.search(user(carla), "fechada", false, 0, 20).items().getFirst()
                .joinRequestPending()).isTrue();
    }

    @Test
    void joinRequestsAreOnlyForPrivateLeaguesWithRoomAndNonMembers() {
        League publicLeague = fixture.league("Liga Aberta", PUBLIC, ana);

        assertCode(() -> fixture.requests.request(user(carla), publicLeague.id()), "LEAGUE_PUBLIC");
        assertCode(() -> fixture.requests.request(user(bruno), privateLeague.id()), "ALREADY_LEAGUE_MEMBER");
        fillUp(privateLeague);
        assertCode(() -> fixture.requests.request(user(carla), privateLeague.id()), "LEAGUE_FULL");
    }

    @Test
    void requesterCancelsTheirPendingRequest() {
        fixture.requests.request(user(carla), privateLeague.id());

        fixture.requests.cancel(user(carla), privateLeague.id());

        assertThat(fixture.joinRequests.all().getFirst().status()).isEqualTo(LeagueEntryStatus.CANCELLED);
        assertCode(() -> fixture.requests.cancel(user(carla), privateLeague.id()), "JOIN_REQUEST_NOT_FOUND");
        assertThat(fixture.queryLeagues.get(user(carla), privateLeague.id()).joinRequestPending()).isFalse();
    }

    @Test
    void leagueAdministratorAcceptsAndTheRequesterBecomesAMember() {
        UUID requestId = requestFrom(carla);
        assertThat(fixture.requests.pending(user(ana), privateLeague.id()))
                .extracting(JoinRequests.PendingRequest::displayName).containsExactly("Carla");
        assertThatThrownBy(() -> fixture.requests.accept(user(bruno), privateLeague.id(), requestId))
                .isInstanceOf(ForbiddenException.class);

        fixture.requests.accept(user(ana), privateLeague.id(), requestId);

        LeagueMember member = fixture.leagues.find(privateLeague.id(), carla.id()).orElseThrow();
        assertThat(member.role()).isEqualTo(LeagueRole.MEMBER);
        assertThat(member.joinedAt()).isEqualTo(NOW);
        JoinRequest request = fixture.joinRequests.findById(requestId).orElseThrow();
        assertThat(request.status()).isEqualTo(LeagueEntryStatus.ACCEPTED);
        assertThat(request.decidedBy()).isEqualTo(ana.id());
        Notification answer = fixture.notifications.of(carla.id()).getFirst();
        assertThat(answer.type()).isEqualTo(NotificationType.LEAGUE_JOIN_REQUEST_ACCEPTED);
        assertThat(answer.body()).isEqualTo("Seu pedido para entrar na liga Liga Fechada foi aceito.");
        assertCode(() -> fixture.requests.accept(user(ana), privateLeague.id(), requestId),
                "JOIN_REQUEST_NOT_PENDING");
    }

    @Test
    void rejectedRequesterIsNotifiedAndMayAskAgain() {
        UUID requestId = requestFrom(carla);

        fixture.requests.reject(user(ana), privateLeague.id(), requestId);

        assertThat(fixture.leagues.find(privateLeague.id(), carla.id())).isEmpty();
        assertThat(fixture.notifications.of(carla.id()).getFirst().body())
                .isEqualTo("Seu pedido para entrar na liga Liga Fechada foi recusado.");
        assertThat(fixture.requests.request(user(carla), privateLeague.id()).joinRequestPending()).isTrue();
    }

    @Test
    void acceptingIntoAFullLeagueFails() {
        UUID requestId = requestFrom(carla);
        fillUp(privateLeague);

        assertCode(() -> fixture.requests.accept(user(ana), privateLeague.id(), requestId), "LEAGUE_FULL");
    }

    @Test
    void requestFromAnotherLeagueIsNotFound() {
        UUID requestId = requestFrom(carla);
        League other = fixture.league("Outra", PRIVATE, ana);

        assertCode(() -> fixture.requests.accept(user(ana), other.id(), requestId), "JOIN_REQUEST_NOT_FOUND");
    }

    @Test
    void invitationByExactEmailNotifiesTheInvitee() {
        fixture.players.withEmail(carla, "carla@example.com");

        fixture.invites.invite(user(ana), privateLeague.id(), "  Carla@Example.com ");
        fixture.invites.invite(user(ana), privateLeague.id(), "carla@example.com");

        assertThat(fixture.invitations.all()).hasSize(1);
        LeagueInvitation invitation = fixture.invitations.all().getFirst();
        assertThat(invitation.playerId()).isEqualTo(carla.id());
        assertThat(invitation.invitedBy()).isEqualTo(ana.id());
        Notification notification = fixture.notifications.of(carla.id()).getFirst();
        assertThat(notification.type()).isEqualTo(NotificationType.LEAGUE_INVITATION_RECEIVED);
        assertThat(notification.body()).isEqualTo("Ana convidou você para a liga Liga Fechada.");
        assertThat(notification.invitationId()).isEqualTo(invitation.id());
        assertThat(notification.leagueId()).isEqualTo(privateLeague.id());
    }

    @Test
    void invitingUnknownEmailsOrMembersLooksTheSameAndDoesNothing() {
        fixture.players.withEmail(bruno, "bruno@example.com");

        fixture.invites.invite(user(ana), privateLeague.id(), "ninguem@example.com");
        fixture.invites.invite(user(ana), privateLeague.id(), "bruno@example.com");

        assertThat(fixture.invitations.all()).isEmpty();
        assertThat(fixture.notifications.all()).isEmpty();
        assertCode(() -> fixture.invites.invite(user(ana), privateLeague.id(), "sem-arroba"), "INVALID_EMAIL");
        assertThatThrownBy(() -> fixture.invites.invite(user(bruno), privateLeague.id(), "x@example.com"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void inviteeAcceptsOrRejects() {
        fixture.players.withEmail(carla, "carla@example.com");
        fixture.players.withEmail(diego, "diego@example.com");
        fixture.invites.invite(user(ana), privateLeague.id(), "carla@example.com");
        fixture.invites.invite(user(ana), privateLeague.id(), "diego@example.com");

        LeagueInvitations.ReceivedInvitation received = fixture.invites.received(user(carla)).getFirst();
        assertThat(received.league().name()).isEqualTo("Liga Fechada");
        assertThat(received.memberCount()).isEqualTo(2);
        assertThat(received.invitedByName()).isEqualTo("Ana");
        UUID carlaInvitation = received.invitation().id();
        UUID diegoInvitation = fixture.invites.received(user(diego)).getFirst().invitation().id();

        assertCode(() -> fixture.invites.accept(user(diego), carlaInvitation), "INVITATION_NOT_FOUND");
        LeagueView view = fixture.invites.accept(user(carla), carlaInvitation);
        fixture.invites.reject(user(diego), diegoInvitation);

        assertThat(view.myRole()).isEqualTo(LeagueRole.MEMBER);
        assertThat(view.memberCount()).isEqualTo(3);
        assertThat(fixture.leagues.find(privateLeague.id(), diego.id())).isEmpty();
        assertThat(fixture.invites.received(user(diego))).isEmpty();
        assertCode(() -> fixture.invites.accept(user(diego), diegoInvitation), "INVITATION_NOT_PENDING");
    }

    @Test
    void inviteCodeLetsAnyoneJoinUntilItIsRegenerated() {
        assertThat(fixture.inviteCodes.current(user(ana), privateLeague.id())).isNull();
        assertThatThrownBy(() -> fixture.inviteCodes.regenerate(user(bruno), privateLeague.id()))
                .isInstanceOf(ForbiddenException.class);
        String first = fixture.inviteCodes.regenerate(user(ana), privateLeague.id());

        LeagueView view = fixture.inviteCodes.join(user(carla), " " + first.toLowerCase() + " ");
        assertThat(view.myRole()).isEqualTo(LeagueRole.MEMBER);
        assertThat(view.league().id()).isEqualTo(privateLeague.id());

        String second = fixture.inviteCodes.regenerate(user(ana), privateLeague.id());
        assertThat(second).isNotEqualTo(first);
        assertThat(fixture.inviteCodes.current(user(ana), privateLeague.id())).isEqualTo(second);
        assertCode(() -> fixture.inviteCodes.join(user(diego), first), "INVITE_CODE_INVALID");
        assertCode(() -> fixture.inviteCodes.join(user(diego), ""), "INVITE_CODE_INVALID");
    }

    @Test
    void archivedOrFullLeaguesCannotBeJoinedByCode() {
        String code = fixture.inviteCodes.regenerate(user(ana), privateLeague.id());
        fillUp(privateLeague);
        assertCode(() -> fixture.inviteCodes.join(user(carla), code), "LEAGUE_FULL");

        fixture.archiving.archive(user(ana), privateLeague.id());
        assertCode(() -> fixture.inviteCodes.join(user(carla), code), "LEAGUE_ARCHIVED");
    }

    @Test
    void joiningAnyWayCancelsTheOtherPendingRequestAndInvitation() {
        fixture.players.withEmail(carla, "carla@example.com");
        UUID requestId = requestFrom(carla);
        fixture.invites.invite(user(ana), privateLeague.id(), "carla@example.com");
        String code = fixture.inviteCodes.regenerate(user(ana), privateLeague.id());

        fixture.inviteCodes.join(user(carla), code);

        assertThat(fixture.joinRequests.findById(requestId).orElseThrow().status())
                .isEqualTo(LeagueEntryStatus.CANCELLED);
        assertThat(fixture.invitations.all().getFirst().status()).isEqualTo(LeagueEntryStatus.CANCELLED);
        assertCode(() -> fixture.requests.accept(user(ana), privateLeague.id(), requestId),
                "JOIN_REQUEST_NOT_PENDING");
    }

    private UUID requestFrom(Player player) {
        fixture.requests.request(user(player), privateLeague.id());
        return fixture.joinRequests.findPending(privateLeague.id(), player.id()).orElseThrow().id();
    }

    private void fillUp(League league) {
        int missing = League.MAX_MEMBERS - fixture.leagues.count(league.id());
        IntStream.range(0, missing).forEach(i -> fixture.leagues.insert(
                new LeagueMember(league.id(), fixture.player("Extra " + i).id(), LeagueRole.MEMBER, NOW)));
    }

    private static void assertCode(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(code);
    }
}
