package com.lucchetta.core.usecase.match;

import com.lucchetta.core.domain.PlacementPoints;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.RankingType;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.domain.league.LeagueVisibility;
import com.lucchetta.core.domain.league.ScoreEntry;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.domain.match.Score;
import com.lucchetta.core.domain.match.Team;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.scoring.NotCountedReason;
import com.lucchetta.core.support.DirectTransactionRunner;
import com.lucchetta.core.support.InMemoryMatchAudit;
import com.lucchetta.core.support.InMemoryNotifications;
import com.lucchetta.core.support.TestLeague;
import com.lucchetta.core.usecase.match.admin.MatchAdministration;
import com.lucchetta.core.usecase.notification.MatchNotificationComposer;
import com.lucchetta.core.usecase.notification.MatchNotifier;
import com.lucchetta.core.usecase.notification.PushDispatcher;
import com.lucchetta.core.usecase.ranking.GetSeasonRanking;
import com.lucchetta.core.usecase.ranking.RankingFixture;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Decision D-031: a match whose four players are members of another league counts there too. */
class SharedMatchesTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 4);

    private final RankingFixture world = new RankingFixture();
    /** Another league where Ana, Beto, Caio and Dani also play. */
    private final League other = world.league.leagues.add(new League(UUID.randomUUID(), "Outra Liga", null,
            LeagueVisibility.PUBLIC, null, null, CREATED, CREATED, null, null, 0));
    private final Season otherSeason;
    private final GetSeasonRanking ranking = new GetSeasonRanking(world.access, world.querySeasons, world.players,
            world.league.leagues, world.leagueMatches, world.stages);

    {
        for (Player player : List.of(world.ana, world.beto, world.caio, world.dani)) {
            world.league.leagues.insert(new LeagueMember(other.id(), player.id(), LeagueRole.MEMBER, CREATED));
        }
        otherSeason = world.seasons.add(new Season(UUID.randomUUID(), other.id(), "Outra Primavera",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), world.ana.id(), CREATED, CREATED));
    }

    @Test
    void matchOfAnotherLeagueWithTheSameFourMembersCountsInBothRankings() {
        Match elsewhere = matchIn(other.id(), fourMembers());

        MatchDetails here = world.leagueMatches.all(TestLeague.ID).stream()
                .filter(d -> d.match().id().equals(elsewhere.id())).findFirst().orElseThrow();
        assertThat(here.season()).isEqualTo(world.activeSeason);
        assertThat(here.eligibility().counted()).isTrue();
        assertThat(here.pointsFor(Team.A)).isEqualTo(9);
        assertThat(pointsHere(world.ana)).isEqualTo(9);
        assertThat(world.leagueMatches.all(other.id())).extracting(d -> d.season().id())
                .containsExactly(otherSeason.id());

        SearchMatches history = new SearchMatches(world.access, world.matches, world.querySeasons, world.assembler,
                world.sharedMatches);
        assertThat(history.execute(RankingFixture.user(world.ana), TestLeague.ID,
                new SearchMatches.Criteria(null, null, null, null, null, 0, 20)).items())
                .extracting(d -> d.match().id()).containsExactly(elsewhere.id());
        QueryMatch query = new QueryMatch(world.access, world.matches, new InMemoryMatchAudit(), world.assembler,
                world.sharedMatches);
        assertThat(query.byId(RankingFixture.user(world.ana), TestLeague.ID, elsewhere.id()).season())
                .isEqualTo(world.activeSeason);
    }

    @Test
    void ownAndSharedMatchesAreMergedMostRecentFirst() {
        Match own = world.match(DAY.plusDays(1), 6, 2);
        Match elsewhere = matchIn(other.id(), fourMembers());
        Match earlier = world.match(DAY.minusDays(1), 6, 3);

        assertThat(world.leagueMatches.all(TestLeague.ID)).extracting(d -> d.match().id())
                .containsExactly(own.id(), elsewhere.id(), earlier.id());
    }

    @Test
    void matchWithAGuestOrAPlayerOutsideTheLeagueIsNotShared() {
        matchIn(other.id(), List.of(
                Participant.player(Team.A, 1, world.ana.id()), Participant.guest(Team.A, 2, "Zé"),
                Participant.player(Team.B, 1, world.caio.id()), Participant.player(Team.B, 2, world.dani.id())));
        Player outsider = world.players.add("Gil");
        world.league.leagues.insert(new LeagueMember(other.id(), outsider.id(), LeagueRole.MEMBER, CREATED));
        matchIn(other.id(), List.of(
                Participant.player(Team.A, 1, world.ana.id()), Participant.player(Team.A, 2, outsider.id()),
                Participant.player(Team.B, 1, world.caio.id()), Participant.player(Team.B, 2, world.dani.id())));

        assertThat(world.leagueMatches.all(TestLeague.ID)).isEmpty();
        assertThat(pointsHere(world.ana)).isZero();
    }

    @Test
    void anyCurrentMemberBringsTheMatchesEvenWhenJoiningLater() {
        Player late = world.players.add("Gil", Instant.parse("2026-11-01T12:00:00Z"));
        world.league.leagues.insert(new LeagueMember(other.id(), late.id(), LeagueRole.MEMBER, CREATED));
        Match elsewhere = matchIn(other.id(), List.of(
                Participant.player(Team.A, 1, world.ana.id()), Participant.player(Team.A, 2, late.id()),
                Participant.player(Team.B, 1, world.caio.id()), Participant.player(Team.B, 2, world.dani.id())));
        assertThat(world.leagueMatches.all(TestLeague.ID)).isEmpty();

        world.league.members(late);

        assertThat(world.leagueMatches.all(TestLeague.ID)).extracting(d -> d.match().id())
                .containsExactly(elsewhere.id());
    }

    @Test
    void leagueMayTurnSharedMatchesOff() {
        matchIn(other.id(), fourMembers());
        League here = world.league.leagues.findById(TestLeague.ID).orElseThrow();

        world.league.leagues.update(here.withSharedMatches(false, CREATED));

        assertThat(world.leagueMatches.all(TestLeague.ID)).isEmpty();
        assertThat(world.leagueMatches.all(other.id())).hasSize(1);
    }

    @Test
    void sharedOnlyWhereTheLeagueRanksByPointsPerMatchOnAPlayingDay() {
        // 2026-09-06: TestLeague's past season; 2027-01-10: no season in TestLeague.
        matchIn(other.id(), fourMembers(), LocalDate.of(2027, 1, 10));
        Season tournament = world.seasons.add(new Season(UUID.randomUUID(), TestLeague.ID, "Torneio",
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28), EnumSet.allOf(DayOfWeek.class), List.of(),
                RankingType.DAILY_TOURNAMENT, PlacementPoints.DEFAULT, world.ana.id(), CREATED, CREATED));
        matchIn(other.id(), fourMembers(), tournament.startDate().plusDays(1));
        Season mondays = world.seasons.add(new Season(UUID.randomUUID(), TestLeague.ID, "Segundas",
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31), EnumSet.of(DayOfWeek.MONDAY), world.ana.id(),
                CREATED, CREATED));
        matchIn(other.id(), fourMembers(), LocalDate.of(2027, 3, 3)); // a Wednesday
        Match monday = matchIn(other.id(), fourMembers(), LocalDate.of(2027, 3, 8));

        assertThat(world.leagueMatches.all(TestLeague.ID)).extracting(d -> d.match().id())
                .containsExactly(monday.id());
        assertThat(mondays.isPlayingDay(LocalDate.of(2027, 3, 8))).isTrue();
    }

    @Test
    void voidedMatchIsSharedAsVoided() {
        matchIn(other.id(), fourMembers(), DAY, MatchStatus.VOIDED);

        assertThat(world.leagueMatches.all(TestLeague.ID)).singleElement()
                .satisfies(d -> assertThat(d.eligibility().reason()).isEqualTo(NotCountedReason.VOIDED));
    }

    @Test
    void onlyTheLeagueWhereTheMatchWasRegisteredChangesIt() {
        Match elsewhere = matchIn(other.id(), fourMembers());
        Player admin = world.players.add("Admin");
        world.league.administrator(admin);
        InMemoryNotifications notifications = new InMemoryNotifications();
        MatchAdministration administration = new MatchAdministration(world.access, world.matches,
                new InMemoryMatchAudit(), new MatchValidator(world.league.leagues), world.assembler,
                new MatchNotifier(notifications, world.league.leagues, new MatchNotificationComposer(),
                        RankingFixture.CLOCK),
                new PushDispatcher(notifications, notifications), new DirectTransactionRunner(), RankingFixture.CLOCK);

        assertThatThrownBy(() -> administration.voidMatch(RankingFixture.user(admin), TestLeague.ID, elsewhere.id(),
                "teste")).isInstanceOf(NotFoundException.class).hasMessage("MATCH_NOT_FOUND");
        assertThat(world.matches.findById(elsewhere.id()).orElseThrow().status()).isEqualTo(MatchStatus.VALID);
    }

    @Test
    void creatingALeagueReceivesSharedMatchesUnlessTurnedOff() {
        assertThat(League.create("Nova", null, LeagueVisibility.PUBLIC, ScoreEntry.MEMBERS, true, world.ana.id(),
                CREATED).sharedMatches()).isTrue();
        assertThat(other.sharedMatches()).isTrue();
    }

    private int pointsHere(Player player) {
        return ranking.current(RankingFixture.user(world.ana), TestLeague.ID).orElseThrow().entries().stream()
                .filter(e -> e.playerId().equals(player.id()))
                .mapToInt(e -> e.stats().points())
                .findFirst().orElseThrow();
    }

    private List<Participant> fourMembers() {
        return List.of(
                Participant.player(Team.A, 1, world.ana.id()), Participant.player(Team.A, 2, world.beto.id()),
                Participant.player(Team.B, 1, world.caio.id()), Participant.player(Team.B, 2, world.dani.id()));
    }

    private Match matchIn(UUID leagueId, List<Participant> participants) {
        return matchIn(leagueId, participants, DAY);
    }

    private Match matchIn(UUID leagueId, List<Participant> participants, LocalDate day) {
        return matchIn(leagueId, participants, day, MatchStatus.VALID);
    }

    private Match matchIn(UUID leagueId, List<Participant> participants, LocalDate day, MatchStatus status) {
        Instant playedAt = day.atTime(LocalTime.NOON).atZone(RankingFixture.BRASILIA).toInstant();
        Match match = new Match(UUID.randomUUID(), leagueId, playedAt,
                new Score(MatchFormat.STANDARD, 6, 4, null, null), participants, MatchStatus.VALID, world.ana.id(),
                playedAt, playedAt);
        return world.matches.add(status == MatchStatus.VOIDED ? match.voided("teste", world.ana.id(), playedAt)
                : match);
    }
}
