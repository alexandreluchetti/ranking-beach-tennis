package com.lucchetta.core.domain.tournament;

import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.exception.RuleViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * One playing day of a daily tournament season (D-027): who is present, the pairs and groups
 * drawn for the day and its games. Groups play round robin; the first two of each group go to the
 * knockout, which is drawn only once every group game has a score and then advances round by
 * round. Scores live in the matches; this aggregate only links each game to its match, so every
 * decision that needs results receives them as {@code results} (fixture number to games).
 * <ul>
 *     <li>4–5 pairs, one group: final between the first and the second.</li>
 *     <li>6–8 pairs, two groups: semifinals A1×B2 and B1×A2, then the final.</li>
 *     <li>9–12 pairs, three groups: the two best group winners go straight to the semifinals;
 *     quarterfinals third winner × runner-up of the second winner's group and runner-up of the
 *     best winner's group × runner-up of the third winner's group.</li>
 *     <li>13–16 pairs, four groups: quarterfinals A1×B2, C1×D2, B1×A2, D1×C2, then semifinals
 *     and final.</li>
 * </ul>
 * Two pairs of the same group meet again only in the final.
 *
 * @param seasonId the daily tournament season the day belongs to
 * @param format   format of every game of the stage, chosen by the administrator (D-027)
 * @param entrants present players, in the order the administrator listed them
 */
public record TournamentStage(
        UUID id,
        UUID leagueId,
        UUID seasonId,
        LocalDate date,
        MatchFormat format,
        List<StageEntrant> entrants,
        List<StagePair> pairs,
        List<StageFixture> fixtures,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        int version) {

    public static final int MIN_PLAYERS = 8;
    public static final int MAX_PLAYERS = 32;

    public TournamentStage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(leagueId, "leagueId");
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (format == null) {
            throw new RuleViolationException("INVALID_STAGE_FORMAT");
        }
        entrants = validatedEntrants(entrants);
        pairs = pairs.stream().sorted(Comparator.comparingInt(StagePair::number)).toList();
        fixtures = fixtures.stream().sorted(Comparator.comparingInt(StageFixture::number)).toList();
    }

    /** A new stage with its first draw. */
    public static TournamentStage create(UUID leagueId, UUID seasonId, LocalDate date, MatchFormat format,
                                         List<StageEntrant> entrants, UUID createdBy, RandomGenerator random,
                                         Instant now) {
        List<StageEntrant> valid = validatedEntrants(entrants);
        List<StagePair> pairs = drawPairs(valid, random);
        return new TournamentStage(UUID.randomUUID(), leagueId, seasonId, date, format, valid, pairs,
                groupFixtures(pairs), createdBy, now, now, 0);
    }

    /**
     * A new draw, possibly with a new list of players and format, allowed until the first score
     * is entered (D-027).
     */
    public TournamentStage redrawn(List<StageEntrant> newEntrants, MatchFormat newFormat, RandomGenerator random,
                                   Instant now) {
        requireNotStarted();
        List<StageEntrant> valid = validatedEntrants(newEntrants);
        List<StagePair> newPairs = drawPairs(valid, random);
        return new TournamentStage(id, leagueId, seasonId, date, newFormat, valid, newPairs,
                groupFixtures(newPairs), createdBy, createdAt, now, version);
    }

    public void requireNotStarted() {
        if (fixtures.stream().anyMatch(StageFixture::scored)) {
            throw new RuleViolationException("STAGE_STARTED");
        }
    }

    public int groupCount() {
        return groupCount(pairs.size());
    }

    public Optional<StageFixture> fixture(int number) {
        return fixtures.stream().filter(f -> f.number() == number).findFirst();
    }

    public Optional<StageFixture> fixtureOfMatch(UUID matchId) {
        return fixtures.stream().filter(f -> matchId.equals(f.matchId())).findFirst();
    }

    public StagePair pair(int number) {
        return pairs.stream().filter(p -> p.number() == number).findFirst().orElseThrow();
    }

    public StageEntrant entrant(int position) {
        return entrants.stream().filter(e -> e.position() == position).findFirst().orElseThrow();
    }

    public boolean knockoutDrawn() {
        return fixtures.stream().anyMatch(f -> f.round() != StageRound.GROUP);
    }

    /** The final has a score: placements are known. */
    public boolean finished() {
        return fixtures.stream().anyMatch(f -> f.round() == StageRound.FINAL && f.scored());
    }

    /**
     * Checks that the score of this game may be entered or changed, and drops the later knockout
     * games that have no score yet, since its winner may change: {@link #advanced} draws them
     * again. A group game cannot change once the knockout is drawn: the administrator reopens the
     * groups first (D-027).
     */
    public TournamentStage openForScore(int fixtureNumber, Instant now) {
        StageFixture fixture = fixture(fixtureNumber)
                .orElseThrow(() -> new RuleViolationException("STAGE_FIXTURE_NOT_FOUND"));
        if (fixture.round() == StageRound.GROUP) {
            if (knockoutDrawn()) {
                throw new RuleViolationException("STAGE_GROUPS_CLOSED");
            }
            return this;
        }
        List<StageFixture> later = fixtures.stream()
                .filter(f -> f.round().compareTo(fixture.round()) > 0)
                .toList();
        if (later.stream().anyMatch(StageFixture::scored)) {
            throw new RuleViolationException("STAGE_LATER_ROUND_SCORED");
        }
        return later.isEmpty() ? this : withFixtures(fixtures.stream().filter(f -> !later.contains(f)).toList(), now);
    }

    /** Removes the knockout so group scores can be corrected; only before any knockout score. */
    public TournamentStage groupsReopened(Instant now) {
        if (!knockoutDrawn()) {
            throw new RuleViolationException("STAGE_GROUPS_NOT_CLOSED");
        }
        if (fixtures.stream().anyMatch(f -> f.round() != StageRound.GROUP && f.scored())) {
            throw new RuleViolationException("STAGE_KNOCKOUT_STARTED");
        }
        return withFixtures(fixtures.stream().filter(f -> f.round() == StageRound.GROUP).toList(), now);
    }

    public TournamentStage withFixtureMatch(int fixtureNumber, UUID matchId, Instant now) {
        return withFixtures(fixtures.stream()
                .map(f -> f.number() == fixtureNumber ? f.withMatch(matchId) : f)
                .toList(), now);
    }

    /**
     * Draws the next round once every game so far has a score: the knockout after the groups,
     * then semifinals and the final. Returns this stage when there is nothing to draw.
     */
    public TournamentStage advanced(Map<Integer, FixtureResult> results, Instant now) {
        if (fixtures.isEmpty() || !fixtures.stream().allMatch(f -> f.scored() && results.containsKey(f.number()))) {
            return this;
        }
        StageRound last = fixtures.getLast().round();
        List<int[]> next = switch (last) {
            case GROUP -> firstKnockoutRound(results);
            case QUARTERFINAL -> groupCount() == 3 ? semifinalsWithByes(results) : pairedWinners(last, results);
            case SEMIFINAL -> pairedWinners(last, results);
            case FINAL -> List.of();
        };
        if (next.isEmpty()) {
            return this;
        }
        StageRound round = switch (last) {
            case GROUP -> groupCount() == 1 ? StageRound.FINAL
                    : groupCount() == 2 ? StageRound.SEMIFINAL : StageRound.QUARTERFINAL;
            case QUARTERFINAL -> StageRound.SEMIFINAL;
            default -> StageRound.FINAL;
        };
        List<StageFixture> all = new ArrayList<>(fixtures);
        int number = fixtures.getLast().number();
        for (int[] game : next) {
            all.add(new StageFixture(++number, round, null, game[0], game[1], null));
        }
        return withFixtures(all, now);
    }

    /** Standings of every group, best first, from the scored group games. */
    public Map<Integer, List<GroupStanding>> standings(Map<Integer, FixtureResult> results) {
        Map<Integer, GroupStanding> byPair = new LinkedHashMap<>();
        pairs.forEach(p -> byPair.put(p.number(), new GroupStanding(p.number(), p.group(), 0, 0, 0, 0)));
        for (StageFixture f : fixtures) {
            FixtureResult result = results.get(f.number());
            if (f.round() != StageRound.GROUP || !f.scored() || result == null) {
                continue;
            }
            byPair.computeIfPresent(f.pairA(), (k, s) -> s.plus(result.pairAGames(), result.pairBGames()));
            byPair.computeIfPresent(f.pairB(), (k, s) -> s.plus(result.pairBGames(), result.pairAGames()));
        }
        return byPair.values().stream().collect(Collectors.groupingBy(GroupStanding::group, TreeMap::new,
                Collectors.collectingAndThen(Collectors.toList(),
                        list -> list.stream().sorted(GroupStanding.GROUP_ORDER).toList())));
    }

    /** Placement of every pair, by pair number; empty until the final has a score (D-028). */
    public Map<Integer, Placement> placements(Map<Integer, FixtureResult> results) {
        if (!finished()) {
            return Map.of();
        }
        Map<Integer, Placement> placements = new TreeMap<>();
        pairs.forEach(p -> placements.put(p.number(), Placement.GROUP_STAGE));
        for (StageFixture f : fixtures) {
            if (f.round() == StageRound.GROUP) {
                continue;
            }
            FixtureResult result = results.get(f.number());
            int winner = result.pairAWon() ? f.pairA() : f.pairB();
            int loser = result.pairAWon() ? f.pairB() : f.pairA();
            placements.put(loser, switch (f.round()) {
                case QUARTERFINAL -> Placement.QUARTERFINAL;
                case SEMIFINAL -> Placement.SEMIFINAL;
                default -> Placement.RUNNER_UP;
            });
            if (f.round() == StageRound.FINAL) {
                placements.put(winner, Placement.CHAMPION);
            }
        }
        return placements;
    }

    static int groupCount(int pairCount) {
        if (pairCount <= 5) {
            return 1;
        }
        return pairCount <= 8 ? 2 : pairCount <= 12 ? 3 : 4;
    }

    private TournamentStage withFixtures(List<StageFixture> newFixtures, Instant now) {
        return new TournamentStage(id, leagueId, seasonId, date, format, entrants, pairs, newFixtures, createdBy,
                createdAt, now, version);
    }

    private List<int[]> firstKnockoutRound(Map<Integer, FixtureResult> results) {
        List<List<GroupStanding>> groups = new ArrayList<>(standings(results).values());
        return switch (groups.size()) {
            case 1 -> List.of(game(groups, 0, 0, 0, 1));
            case 2 -> List.of(game(groups, 0, 0, 1, 1), game(groups, 1, 0, 0, 1));
            case 3 -> {
                List<List<GroupStanding>> byWinner = groupsByWinner(groups);
                yield List.of(game(byWinner, 2, 0, 1, 1), game(byWinner, 0, 1, 2, 1));
            }
            default -> List.of(game(groups, 0, 0, 1, 1), game(groups, 2, 0, 3, 1),
                    game(groups, 1, 0, 0, 1), game(groups, 3, 0, 2, 1));
        };
    }

    /** Semifinals of three groups: best winner × first quarterfinal, second winner × second. */
    private List<int[]> semifinalsWithByes(Map<Integer, FixtureResult> results) {
        List<List<GroupStanding>> byWinner = groupsByWinner(new ArrayList<>(standings(results).values()));
        List<Integer> winners = winnersOf(StageRound.QUARTERFINAL, results);
        return List.of(new int[]{byWinner.get(0).getFirst().pair(), winners.get(0)},
                new int[]{byWinner.get(1).getFirst().pair(), winners.get(1)});
    }

    private List<int[]> pairedWinners(StageRound round, Map<Integer, FixtureResult> results) {
        List<Integer> winners = winnersOf(round, results);
        return IntStream.range(0, winners.size() / 2)
                .mapToObj(i -> new int[]{winners.get(2 * i), winners.get(2 * i + 1)})
                .toList();
    }

    private List<Integer> winnersOf(StageRound round, Map<Integer, FixtureResult> results) {
        return fixtures.stream()
                .filter(f -> f.round() == round)
                .map(f -> results.get(f.number()).pairAWon() ? f.pairA() : f.pairB())
                .toList();
    }

    private static List<List<GroupStanding>> groupsByWinner(List<List<GroupStanding>> groups) {
        return groups.stream()
                .sorted(Comparator.comparing(List::getFirst, GroupStanding.ACROSS_GROUPS))
                .toList();
    }

    private static int[] game(List<List<GroupStanding>> groups, int groupA, int placeA, int groupB, int placeB) {
        return new int[]{groups.get(groupA).get(placeA).pair(), groups.get(groupB).get(placeB).pair()};
    }

    private static List<StageEntrant> validatedEntrants(List<StageEntrant> entrants) {
        if (entrants == null || entrants.size() < MIN_PLAYERS || entrants.size() > MAX_PLAYERS) {
            throw new RuleViolationException("INVALID_STAGE_PLAYER_COUNT");
        }
        if (entrants.size() % 2 != 0) {
            throw new RuleViolationException("STAGE_ODD_PLAYER_COUNT");
        }
        Set<UUID> players = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (StageEntrant entrant : entrants) {
            if (!positions.add(entrant.position())) {
                throw new IllegalArgumentException("Repeated entrant position");
            }
            if (!entrant.guest() && !players.add(entrant.playerId())) {
                throw new RuleViolationException("DUPLICATE_STAGE_PLAYER");
            }
        }
        return entrants.stream().sorted(Comparator.comparingInt(StageEntrant::position)).toList();
    }

    /** Random pairs; consecutive pairs go to consecutive groups, so group sizes differ by at most one. */
    private static List<StagePair> drawPairs(List<StageEntrant> entrants, RandomGenerator random) {
        List<Integer> positions = new ArrayList<>(entrants.stream().map(StageEntrant::position).toList());
        for (int i = positions.size() - 1; i > 0; i--) {
            Collections.swap(positions, i, random.nextInt(i + 1));
        }
        int pairCount = positions.size() / 2;
        int groups = groupCount(pairCount);
        return IntStream.range(0, pairCount)
                .mapToObj(i -> new StagePair(i + 1, i % groups + 1, positions.get(2 * i), positions.get(2 * i + 1)))
                .toList();
    }

    /**
     * Round robin of every group (circle method), interleaving the groups round by round so that
     * each pair's games are spread over the day.
     */
    private static List<StageFixture> groupFixtures(List<StagePair> pairs) {
        Map<Integer, List<List<int[]>>> roundsByGroup = pairs.stream()
                .collect(Collectors.groupingBy(StagePair::group, TreeMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), TournamentStage::roundRobin)));
        int maxRounds = roundsByGroup.values().stream().mapToInt(List::size).max().orElse(0);
        List<StageFixture> fixtures = new ArrayList<>();
        for (int round = 0; round < maxRounds; round++) {
            for (Map.Entry<Integer, List<List<int[]>>> group : roundsByGroup.entrySet()) {
                if (round < group.getValue().size()) {
                    for (int[] game : group.getValue().get(round)) {
                        fixtures.add(new StageFixture(fixtures.size() + 1, StageRound.GROUP, group.getKey(), game[0],
                                game[1], null));
                    }
                }
            }
        }
        return fixtures;
    }

    private static List<List<int[]>> roundRobin(List<StagePair> group) {
        List<Integer> slots = new ArrayList<>(group.stream().map(StagePair::number).sorted().toList());
        if (slots.size() % 2 != 0) {
            slots.add(null); // bye
        }
        int n = slots.size();
        List<List<int[]>> rounds = new ArrayList<>();
        for (int round = 0; round < n - 1; round++) {
            List<int[]> games = new ArrayList<>();
            for (int i = 0; i < n / 2; i++) {
                Integer a = slots.get(i);
                Integer b = slots.get(n - 1 - i);
                if (a != null && b != null) {
                    games.add(new int[]{a, b});
                }
            }
            rounds.add(games);
            slots.add(1, slots.removeLast());
        }
        return rounds;
    }
}
