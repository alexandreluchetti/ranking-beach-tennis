package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.domain.league.LeagueVisibility;
import com.lucchetta.core.exception.BusinessException;
import com.lucchetta.core.exception.ForbiddenException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static com.lucchetta.core.domain.league.LeagueVisibility.PRIVATE;
import static com.lucchetta.core.domain.league.LeagueVisibility.PUBLIC;
import static com.lucchetta.core.usecase.league.LeagueFixture.systemAdmin;
import static com.lucchetta.core.usecase.league.LeagueFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Creating, editing, archiving and finding leagues (decisions D-017, D-018, D-020). */
class LeagueManagementTest {

    private final LeagueFixture fixture = new LeagueFixture();
    private final Player ana = fixture.player("Ana");
    private final Player bruno = fixture.player("Bruno");

    @Test
    void creatorBecomesTheLeagueAdministrator() {
        LeagueView view = fixture.createLeague.execute(user(ana),
                new CreateLeague.Command("  Liga   do Clube ", "Quartas à noite", PRIVATE));

        assertThat(view.league().name()).isEqualTo("Liga do Clube");
        assertThat(view.league().createdBy()).isEqualTo(ana.id());
        assertThat(view.memberCount()).isEqualTo(1);
        assertThat(view.myRole()).isEqualTo(LeagueRole.ADMIN);
        assertThat(view.canAdminister()).isTrue();
        assertThat(fixture.leagues.find(view.league().id(), ana.id()).orElseThrow().role())
                .isEqualTo(LeagueRole.ADMIN);
    }

    @Test
    void leagueDataIsValidated() {
        assertCode(() -> create(ana, "AB", PUBLIC), "INVALID_LEAGUE_NAME");
        assertCode(() -> create(ana, "A".repeat(61), PUBLIC), "INVALID_LEAGUE_NAME");
        assertCode(() -> create(ana, "Liga", null), "INVALID_LEAGUE_VISIBILITY");
        assertCode(() -> fixture.createLeague.execute(user(ana),
                new CreateLeague.Command("Liga", "x".repeat(501), PUBLIC)), "INVALID_LEAGUE_DESCRIPTION");
    }

    @Test
    void namesAreUniqueIgnoringCase() {
        create(ana, "Liga do Clube", PUBLIC);

        assertCode(() -> create(bruno, "LIGA DO CLUBE", PRIVATE), "LEAGUE_NAME_TAKEN");
    }

    @Test
    void aUserMayHaveAtMostFiveActiveLeaguesExceptTheSystemAdministrator() {
        IntStream.rangeClosed(1, 5).forEach(i -> create(ana, "Liga " + i, PUBLIC));

        assertCode(() -> create(ana, "Liga 6", PUBLIC), "LEAGUE_LIMIT_REACHED");

        League first = fixture.leagues.search("Liga 1", false, 0, 1).getFirst();
        fixture.archiving.archive(user(ana), first.id());
        assertThat(create(ana, "Liga 6", PUBLIC).league().name()).isEqualTo("Liga 6");

        IntStream.rangeClosed(1, 6).forEach(i -> create(bruno, "Admin " + i, PUBLIC, true));
    }

    @Test
    void leagueAdministratorEditsTheLeague() {
        League league = fixture.league("Liga", PUBLIC, ana, bruno);

        LeagueView view = fixture.updateLeague.execute(user(ana), league.id(),
                new UpdateLeague.Command("Liga Nova", "Descrição", PRIVATE));
        assertThat(view.league().name()).isEqualTo("Liga Nova");
        assertThat(view.league().visibility()).isEqualTo(PRIVATE);

        LeagueView unchanged = fixture.updateLeague.execute(user(ana), league.id(),
                new UpdateLeague.Command(null, "", null));
        assertThat(unchanged.league().name()).isEqualTo("Liga Nova");
        assertThat(unchanged.league().description()).isNull();

        assertThatThrownBy(() -> fixture.updateLeague.execute(user(bruno), league.id(),
                new UpdateLeague.Command("Outra", null, null))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void systemAdministratorAdministersAnyLeagueWithoutBeingAMember() {
        Player system = fixture.player("Sistema");
        League league = fixture.league("Liga", PUBLIC, ana);

        LeagueView view = fixture.updateLeague.execute(systemAdmin(system), league.id(),
                new UpdateLeague.Command("Liga Revisada", null, null));

        assertThat(view.myRole()).isNull();
        assertThat(view.canAdminister()).isTrue();
        assertThat(view.memberCount()).isEqualTo(1);
    }

    @Test
    void archivedLeaguesAreReadOnlyAndOnlyTheSystemAdministratorUnarchives() {
        Player system = fixture.player("Sistema");
        League league = fixture.league("Liga", PUBLIC, ana, bruno);
        assertThatThrownBy(() -> fixture.archiving.archive(user(bruno), league.id()))
                .isInstanceOf(ForbiddenException.class);

        LeagueView archived = fixture.archiving.archive(user(ana), league.id());

        assertThat(archived.league().archived()).isTrue();
        assertThat(archived.league().archivedBy()).isEqualTo(ana.id());
        assertCode(() -> fixture.updateLeague.execute(user(ana), league.id(),
                new UpdateLeague.Command("Outra", null, null)), "LEAGUE_ARCHIVED");
        assertCode(() -> fixture.archiving.archive(user(ana), league.id()), "LEAGUE_ARCHIVED");
        assertThatThrownBy(() -> fixture.archiving.unarchive(user(ana), league.id()))
                .isInstanceOf(ForbiddenException.class);

        assertThat(fixture.archiving.unarchive(systemAdmin(system), league.id()).league().archived()).isFalse();
        assertCode(() -> fixture.archiving.unarchive(systemAdmin(system), league.id()), "LEAGUE_NOT_ARCHIVED");
    }

    @Test
    void myLeaguesIncludeArchivedOnesAndSearchHidesThem() {
        League alpha = fixture.league("Alpha", PUBLIC, ana);
        League beta = fixture.league("Beta", PRIVATE, bruno, ana);
        fixture.league("Gama", PRIVATE, bruno);
        fixture.archiving.archive(user(ana), alpha.id());

        assertThat(fixture.queryLeagues.mine(user(ana)))
                .extracting(view -> view.league().name(), LeagueView::myRole)
                .containsExactly(
                        tuple("Alpha", LeagueRole.ADMIN),
                        tuple("Beta", LeagueRole.MEMBER));

        QueryLeagues.Page page = fixture.queryLeagues.search(user(ana), null, false, 0, 20);
        assertThat(page.items()).extracting(view -> view.league().name()).containsExactly("Beta", "Gama");
        assertThat(page.items().getFirst().memberCount()).isEqualTo(2);
        assertThat(page.items().get(1).myRole()).isNull();
        assertThat(fixture.queryLeagues.get(user(ana), beta.id()).memberLimit()).isEqualTo(50);
    }

    @Test
    void searchMatchesNameFragmentsAndPages() {
        fixture.league("Liga Norte", PUBLIC, ana);
        fixture.league("Liga Sul", PUBLIC, ana);
        fixture.league("Arena", PUBLIC, ana);

        QueryLeagues.Page first = fixture.queryLeagues.search(user(bruno), "liga", false, 0, 1);
        assertThat(first.items()).extracting(view -> view.league().name()).containsExactly("Liga Norte");
        assertThat(first.hasNext()).isTrue();
        QueryLeagues.Page second = fixture.queryLeagues.search(user(bruno), "liga", false, 1, 1);
        assertThat(second.items()).extracting(view -> view.league().name()).containsExactly("Liga Sul");
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void onlyTheSystemAdministratorListsArchivedLeagues() {
        Player system = fixture.player("Sistema");
        League league = fixture.league("Liga", PUBLIC, ana);
        fixture.archiving.archive(user(ana), league.id());

        assertThatThrownBy(() -> fixture.queryLeagues.search(user(ana), null, true, 0, 20))
                .isInstanceOf(ForbiddenException.class);
        assertThat(fixture.queryLeagues.search(systemAdmin(system), null, true, 0, 20).items()).hasSize(1);
    }

    private LeagueView create(Player creator, String name, LeagueVisibility visibility) {
        return create(creator, name, visibility, false);
    }

    private LeagueView create(Player creator, String name, LeagueVisibility visibility,
                              boolean asSystemAdmin) {
        return fixture.createLeague.execute(asSystemAdmin ? systemAdmin(creator) : user(creator),
                new CreateLeague.Command(name, null, visibility));
    }

    private static void assertCode(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(code);
    }
}
