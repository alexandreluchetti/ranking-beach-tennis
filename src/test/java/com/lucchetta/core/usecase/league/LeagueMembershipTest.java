package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.exception.BusinessException;
import com.lucchetta.core.exception.ForbiddenException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.stream.IntStream;

import static com.lucchetta.core.domain.league.LeagueVisibility.PRIVATE;
import static com.lucchetta.core.domain.league.LeagueVisibility.PUBLIC;
import static com.lucchetta.core.usecase.league.LeagueFixture.NOW;
import static com.lucchetta.core.usecase.league.LeagueFixture.systemAdmin;
import static com.lucchetta.core.usecase.league.LeagueFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Joining, leaving, removing members and league administrator roles (decisions D-017 to D-019). */
class LeagueMembershipTest {

    private final LeagueFixture fixture = new LeagueFixture();
    private final Player ana = fixture.player("Ana");
    private final Player bruno = fixture.player("Bruno");
    private final Player carla = fixture.player("Carla");

    @Test
    void anyUserJoinsAPublicLeagueOnce() {
        League league = fixture.league("Liga", PUBLIC, ana);

        LeagueView view = fixture.membership.join(user(bruno), league.id());
        LeagueView again = fixture.membership.join(user(bruno), league.id());

        assertThat(view.myRole()).isEqualTo(LeagueRole.MEMBER);
        assertThat(view.canAdminister()).isFalse();
        assertThat(again.memberCount()).isEqualTo(2);
        assertThat(fixture.leagues.find(league.id(), bruno.id()).orElseThrow().joinedAt()).isEqualTo(NOW);
    }

    @Test
    void privateAndArchivedLeaguesCannotBeJoinedDirectly() {
        League privateLeague = fixture.league("Privada", PRIVATE, ana);
        League archived = fixture.league("Arquivada", PUBLIC, ana);
        fixture.archiving.archive(user(ana), archived.id());

        assertCode(() -> fixture.membership.join(user(bruno), privateLeague.id()), "LEAGUE_PRIVATE");
        assertCode(() -> fixture.membership.join(user(bruno), archived.id()), "LEAGUE_ARCHIVED");
        assertCode(() -> fixture.membership.join(user(bruno), UUID.randomUUID()), "LEAGUE_NOT_FOUND");
    }

    @Test
    void aFullLeagueAcceptsNobody() {
        League league = fixture.league("Liga", PUBLIC, ana);
        IntStream.range(1, League.MAX_MEMBERS).forEach(i -> fixture.leagues.insert(
                new LeagueMember(league.id(), fixture.player("Jogador " + i).id(), LeagueRole.MEMBER, NOW)));

        assertCode(() -> fixture.membership.join(user(bruno), league.id()), "LEAGUE_FULL");
        assertThat(fixture.queryLeagues.get(user(bruno), league.id()).memberCount()).isEqualTo(50);
    }

    @Test
    void membersLeaveButTheLastAdministratorCannot() {
        League league = fixture.league("Liga", PUBLIC, ana, bruno);

        fixture.membership.leave(user(bruno), league.id());
        assertThat(fixture.leagues.find(league.id(), bruno.id())).isEmpty();

        assertCode(() -> fixture.membership.leave(user(ana), league.id()), "LAST_LEAGUE_ADMIN");
        assertCode(() -> fixture.membership.leave(user(carla), league.id()), "LEAGUE_MEMBER_NOT_FOUND");
    }

    @Test
    void leagueAdministratorPromotesAndRevokesIncludingTheCreator() {
        League league = fixture.league("Liga", PUBLIC, ana, bruno);

        MemberView promoted = fixture.membership.changeRole(user(ana), league.id(), bruno.id(), LeagueRole.ADMIN);
        assertThat(promoted.member().role()).isEqualTo(LeagueRole.ADMIN);
        assertThat(promoted.displayName()).isEqualTo("Bruno");

        fixture.membership.changeRole(user(bruno), league.id(), ana.id(), LeagueRole.MEMBER);
        assertThat(fixture.leagues.find(league.id(), ana.id()).orElseThrow().role()).isEqualTo(LeagueRole.MEMBER);
        assertThatThrownBy(() -> fixture.membership.changeRole(user(ana), league.id(), carla.id(), LeagueRole.ADMIN))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void anAdministratorMayRevokeThemselvesUnlessLast() {
        League league = fixture.league("Liga", PUBLIC, ana, bruno);

        assertCode(() -> fixture.membership.changeRole(user(ana), league.id(), ana.id(), LeagueRole.MEMBER),
                "LAST_LEAGUE_ADMIN");

        fixture.membership.changeRole(user(ana), league.id(), bruno.id(), LeagueRole.ADMIN);
        fixture.membership.changeRole(user(ana), league.id(), ana.id(), LeagueRole.MEMBER);
        assertThat(fixture.leagues.countAdmins(league.id())).isEqualTo(1);
    }

    @Test
    void leagueAdministratorRemovesMembers() {
        League league = fixture.league("Liga", PUBLIC, ana, bruno, carla);

        assertThatThrownBy(() -> fixture.membership.remove(user(bruno), league.id(), carla.id()))
                .isInstanceOf(ForbiddenException.class);
        fixture.membership.remove(user(ana), league.id(), carla.id());

        assertThat(fixture.leagues.find(league.id(), carla.id())).isEmpty();
        assertCode(() -> fixture.membership.remove(user(ana), league.id(), carla.id()), "LEAGUE_MEMBER_NOT_FOUND");
        assertCode(() -> fixture.membership.remove(user(ana), league.id(), ana.id()), "LAST_LEAGUE_ADMIN");
    }

    @Test
    void systemAdministratorManagesMembersOfAnyLeague() {
        Player system = fixture.player("Sistema");
        League league = fixture.league("Liga", PRIVATE, ana, bruno);

        fixture.membership.changeRole(systemAdmin(system), league.id(), bruno.id(), LeagueRole.ADMIN);
        fixture.membership.remove(systemAdmin(system), league.id(), ana.id());

        assertThat(fixture.membership.members(systemAdmin(system), league.id()))
                .extracting(MemberView::displayName).containsExactly("Bruno");
    }

    @Test
    void onlyMembersSeeTheMemberListAdministratorsFirst() {
        League league = fixture.league("Liga", PRIVATE, bruno, carla, ana);

        assertThat(fixture.membership.members(user(ana), league.id()))
                .extracting(view -> view.displayName(), view -> view.member().role())
                .containsExactly(tuple("Bruno", LeagueRole.ADMIN), tuple("Ana", LeagueRole.MEMBER),
                        tuple("Carla", LeagueRole.MEMBER));
        Player outsider = fixture.player("Diego");
        assertThatThrownBy(() -> fixture.membership.members(user(outsider), league.id()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void archivedLeaguesKeepTheirMembersButAllowLeaving() {
        League league = fixture.league("Liga", PUBLIC, ana, bruno);
        fixture.archiving.archive(user(ana), league.id());

        assertCode(() -> fixture.membership.remove(user(ana), league.id(), bruno.id()), "LEAGUE_ARCHIVED");
        assertCode(() -> fixture.membership.changeRole(user(ana), league.id(), bruno.id(), LeagueRole.ADMIN),
                "LEAGUE_ARCHIVED");
        fixture.membership.leave(user(bruno), league.id());
        assertThat(fixture.leagues.count(league.id())).isEqualTo(1);
    }

    private static void assertCode(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(code);
    }
}
