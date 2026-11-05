package com.lucchetta.core.domain.tournament;

import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.exception.RuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TournamentStageTest {

    private static final Instant NOW = Instant.parse("2026-11-03T22:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);

    /** Pair A wins when it has the lower number: deterministic results for the tests. */
    private static final BiPredicate<Integer, Integer> LOWER_NUMBER_WINS = (a, b) -> a < b;

    private final Map<Integer, FixtureResult> results = new HashMap<>();

    @ParameterizedTest(name = "{0} players: {1} groups, {2} group games")
    @CsvSource({
            "8,1,6",
            "10,1,10",
            "12,2,6",
            "14,2,9",
            "16,2,12",
            "18,3,9",
            "24,3,18",
            "26,4,15",
            "32,4,24"})
    void drawsPairsAndGroupsAndARoundRobinInEachGroup(int players, int groups, int groupGames) {
        TournamentStage stage = stage(players);

        assertThat(stage.pairs()).hasSize(players / 2);
        assertThat(stage.groupCount()).isEqualTo(groups);
        assertThat(stage.pairs().stream().flatMap(p -> IntStream.of(p.firstPosition(), p.secondPosition()).boxed()))
                .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, players).boxed().toList());
        Map<Integer, Long> sizes = stage.pairs().stream()
                .collect(Collectors.groupingBy(StagePair::group, Collectors.counting()));
        assertThat(sizes.values()).allSatisfy(size -> assertThat(size).isBetween(3L, 5L));
        assertThat(sizes.values().stream().mapToLong(Long::longValue).max().orElseThrow()
                - sizes.values().stream().mapToLong(Long::longValue).min().orElseThrow()).isLessThanOrEqualTo(1);
        assertThat(stage.fixtures()).hasSize(groupGames)
                .allSatisfy(f -> {
                    assertThat(f.round()).isEqualTo(StageRound.GROUP);
                    assertThat(stage.pair(f.pairA()).group()).isEqualTo(f.group());
                    assertThat(stage.pair(f.pairB()).group()).isEqualTo(f.group());
                });
        Set<Set<Integer>> games = stage.fixtures().stream().map(f -> Set.of(f.pairA(), f.pairB()))
                .collect(Collectors.toSet());
        assertThat(games).hasSize(groupGames); // every two pairs of a group meet exactly once
    }

    @Test
    void drawIsRandom() {
        TournamentStage first = TournamentStage.create(UUID.randomUUID(), UUID.randomUUID(), DAY, MatchFormat.SHORT,
                entrants(16), UUID.randomUUID(), new Random(1), NOW);
        TournamentStage second = TournamentStage.create(UUID.randomUUID(), UUID.randomUUID(), DAY, MatchFormat.SHORT,
                entrants(16), UUID.randomUUID(), new Random(2), NOW);

        assertThat(first.pairs()).isNotEqualTo(second.pairs());
    }

    @ParameterizedTest(name = "{0} players")
    @CsvSource({"6", "34", "0"})
    void needsEightToThirtyTwoPlayers(int players) {
        assertThatThrownBy(() -> stage(players))
                .isInstanceOf(RuleViolationException.class)
                .hasMessage("INVALID_STAGE_PLAYER_COUNT");
    }

    @Test
    void rejectsAnOddNumberOfPlayersOrAPlayerTwice() {
        assertThatThrownBy(() -> stage(9)).hasMessage("STAGE_ODD_PLAYER_COUNT");
        List<StageEntrant> twice = new ArrayList<>(entrants(7));
        twice.add(new StageEntrant(8, twice.getFirst().playerId(), null));
        assertThatThrownBy(() -> create(twice)).hasMessage("DUPLICATE_STAGE_PLAYER");
        assertThatThrownBy(() -> new StageEntrant(1, null, "  ")).hasMessage("INVALID_STAGE_GUEST_NAME");
    }

    /** Placements of a whole stage: champion, runner-up, semifinal, quarterfinal, group stage. */
    @ParameterizedTest(name = "{0} players")
    @CsvSource({
            "8,0,0,2",
            "10,0,0,3",
            "16,2,0,4",
            "20,2,2,4",
            "24,2,2,6",
            "32,2,4,8"})
    void wholeStageGivesEveryPairAPlacement(int players, int semifinalists, int quarterfinalists, int groupStage) {
        TournamentStage stage = playToTheEnd(stage(players));

        Map<Placement, Long> placements = stage.placements(results).values().stream()
                .collect(Collectors.groupingBy(p -> p, Collectors.counting()));
        assertThat(stage.finished()).isTrue();
        assertThat(placements.get(Placement.CHAMPION)).isEqualTo(1);
        assertThat(placements.get(Placement.RUNNER_UP)).isEqualTo(1);
        assertThat(placements.getOrDefault(Placement.SEMIFINAL, 0L)).isEqualTo(semifinalists);
        assertThat(placements.getOrDefault(Placement.QUARTERFINAL, 0L)).isEqualTo(quarterfinalists);
        assertThat(placements.getOrDefault(Placement.GROUP_STAGE, 0L)).isEqualTo(groupStage);
        assertThat(stage.advanced(results, NOW)).isEqualTo(stage);
    }

    @Test
    void knockoutOfFourGroupsKeepsPairsOfTheSameGroupApartUntilTheFinal() {
        TournamentStage stage = scoreAll(stage(32));
        stage = stage.advanced(results, NOW);

        List<StageFixture> quarterfinals = knockout(stage, StageRound.QUARTERFINAL);
        Map<Integer, List<GroupStanding>> standings = stage.standings(results);
        assertThat(quarterfinals).extracting(StageFixture::pairA, StageFixture::pairB).containsExactly(
                org.assertj.core.groups.Tuple.tuple(first(standings, 1), second(standings, 2)),
                org.assertj.core.groups.Tuple.tuple(first(standings, 3), second(standings, 4)),
                org.assertj.core.groups.Tuple.tuple(first(standings, 2), second(standings, 1)),
                org.assertj.core.groups.Tuple.tuple(first(standings, 4), second(standings, 3)));
        assertThat(knockout(stage, StageRound.SEMIFINAL)).isEmpty();

        stage = scoreAll(stage).advanced(results, NOW);
        List<StageFixture> semifinals = knockout(stage, StageRound.SEMIFINAL);
        assertThat(semifinals).hasSize(2);
        TournamentStage drawn = stage;
        assertThat(semifinals).allSatisfy(f ->
                assertThat(drawn.pair(f.pairA()).group()).isNotEqualTo(drawn.pair(f.pairB()).group()));
    }

    @Test
    void withThreeGroupsTheTwoBestWinnersGoStraightToTheSemifinals() {
        TournamentStage stage = scoreAll(stage(24)).advanced(results, NOW);
        Map<Integer, List<GroupStanding>> standings = stage.standings(results);
        Set<Integer> winners = standings.values().stream().map(g -> g.getFirst().pair()).collect(Collectors.toSet());

        List<StageFixture> quarterfinals = knockout(stage, StageRound.QUARTERFINAL);
        assertThat(quarterfinals).hasSize(2);
        Set<Integer> inQuarterfinals = quarterfinals.stream()
                .flatMap(f -> IntStream.of(f.pairA(), f.pairB()).boxed()).collect(Collectors.toSet());
        Set<Integer> byes = new HashSet<>(winners);
        byes.removeAll(inQuarterfinals);
        assertThat(byes).hasSize(2);

        stage = scoreAll(stage).advanced(results, NOW);
        assertThat(knockout(stage, StageRound.SEMIFINAL)).extracting(StageFixture::pairA)
                .containsExactlyInAnyOrderElementsOf(byes);
    }

    @Test
    void knockoutIsDrawnOnlyWhenEveryGroupGameHasAScore() {
        TournamentStage stage = stage(16);
        for (StageFixture f : stage.fixtures().subList(0, stage.fixtures().size() - 1)) {
            stage = score(stage, f);
        }

        assertThat(stage.advanced(results, NOW).knockoutDrawn()).isFalse();
    }

    @Test
    void groupScoresChangeOnlyAfterReopeningTheGroupsBeforeAnyKnockoutScore() {
        TournamentStage stage = scoreAll(stage(16)).advanced(results, NOW);
        TournamentStage drawn = stage;

        assertThatThrownBy(() -> drawn.openForScore(1, NOW)).hasMessage("STAGE_GROUPS_CLOSED");
        TournamentStage reopened = stage.groupsReopened(NOW);
        assertThat(reopened.knockoutDrawn()).isFalse();
        assertThat(reopened.openForScore(1, NOW)).isEqualTo(reopened);
        assertThatThrownBy(() -> reopened.groupsReopened(NOW)).hasMessage("STAGE_GROUPS_NOT_CLOSED");

        TournamentStage semifinalScored = score(stage, knockout(stage, StageRound.SEMIFINAL).getFirst());
        assertThatThrownBy(() -> semifinalScored.groupsReopened(NOW)).hasMessage("STAGE_KNOCKOUT_STARTED");
    }

    @Test
    void changingAKnockoutScoreDropsTheLaterRoundUntilItIsDrawnAgain() {
        TournamentStage stage = scoreAll(stage(16)).advanced(results, NOW);
        stage = scoreAll(stage).advanced(results, NOW);
        StageFixture semifinal = knockout(stage, StageRound.SEMIFINAL).getFirst();
        assertThat(knockout(stage, StageRound.FINAL)).hasSize(1);

        TournamentStage open = stage.openForScore(semifinal.number(), NOW);
        assertThat(knockout(open, StageRound.FINAL)).isEmpty();
        assertThat(open.advanced(results, NOW).fixtures()).isEqualTo(stage.fixtures());

        TournamentStage finished = scoreAll(stage);
        assertThatThrownBy(() -> finished.openForScore(semifinal.number(), NOW))
                .hasMessage("STAGE_LATER_ROUND_SCORED");
        assertThatThrownBy(() -> finished.openForScore(999, NOW)).hasMessage("STAGE_FIXTURE_NOT_FOUND");
    }

    @Test
    void drawCanBeRedoneOnlyBeforeTheFirstScore() {
        TournamentStage stage = stage(8);
        TournamentStage redrawn = stage.redrawn(entrants(10), MatchFormat.STANDARD, new Random(7), NOW);
        assertThat(redrawn.pairs()).hasSize(5);
        assertThat(redrawn.format()).isEqualTo(MatchFormat.STANDARD);
        assertThat(redrawn.id()).isEqualTo(stage.id());

        TournamentStage started = score(stage, stage.fixtures().getFirst());
        assertThatThrownBy(() -> started.redrawn(entrants(8), MatchFormat.SHORT, new Random(7), NOW))
                .hasMessage("STAGE_STARTED");
    }

    @Test
    void groupStandingsOrderByWinsThenGameDifference() {
        TournamentStage stage = stage(8);
        // Pair 1 wins everything; then games decide between equal wins.
        for (StageFixture f : stage.fixtures()) {
            boolean aWins = f.pairA() == 1 || (f.pairB() != 1 && f.pairA() < f.pairB());
            int loserGames = f.pairA() + f.pairB() == 7 ? 0 : 4;
            results.put(f.number(), aWins ? new FixtureResult(6, loserGames) : new FixtureResult(loserGames, 6));
            stage = stage.withFixtureMatch(f.number(), UUID.randomUUID(), NOW);
        }

        List<GroupStanding> group = stage.standings(results).get(1);
        assertThat(group).extracting(GroupStanding::pair).containsExactly(1, 2, 3, 4);
        assertThat(group.getFirst().wins()).isEqualTo(3);
        assertThat(group.getFirst().played()).isEqualTo(3);
    }

    private TournamentStage playToTheEnd(TournamentStage stage) {
        while (!stage.finished()) {
            stage = scoreAll(stage).advanced(results, NOW);
        }
        return stage;
    }

    private TournamentStage scoreAll(TournamentStage stage) {
        for (StageFixture f : stage.fixtures()) {
            if (!f.scored()) {
                stage = score(stage, f);
            }
        }
        return stage;
    }

    private TournamentStage score(TournamentStage stage, StageFixture fixture) {
        boolean aWins = LOWER_NUMBER_WINS.test(fixture.pairA(), fixture.pairB());
        results.put(fixture.number(), aWins ? new FixtureResult(6, 3) : new FixtureResult(2, 6));
        return stage.withFixtureMatch(fixture.number(), UUID.randomUUID(), NOW);
    }

    private static List<StageFixture> knockout(TournamentStage stage, StageRound round) {
        return stage.fixtures().stream().filter(f -> f.round() == round).toList();
    }

    private static int first(Map<Integer, List<GroupStanding>> standings, int group) {
        return standings.get(group).get(0).pair();
    }

    private static int second(Map<Integer, List<GroupStanding>> standings, int group) {
        return standings.get(group).get(1).pair();
    }

    private static TournamentStage stage(int players) {
        return create(entrants(players));
    }

    private static TournamentStage create(List<StageEntrant> entrants) {
        return TournamentStage.create(UUID.randomUUID(), UUID.randomUUID(), DAY, MatchFormat.SHORT, entrants,
                UUID.randomUUID(), new Random(42), NOW);
    }

    /** Members, except every fourth player, who is a guest. */
    private static List<StageEntrant> entrants(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> i % 4 == 0 ? new StageEntrant(i, null, "Convidado " + i)
                        : new StageEntrant(i, UUID.randomUUID(), null))
                .toList();
    }
}
