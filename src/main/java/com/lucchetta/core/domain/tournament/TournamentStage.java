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
import java.util.stream.Stream;

/**
 * One playing day of a daily tournament season (D-027, D-030): who is present, the pairs and
 * groups drawn for the day and its games. Groups play round robin; the first two of each group go
 * to the knockout, which is drawn only once every group game has a score and then advances round
 * by round. When there are semifinals, their losers play for third place, drawn with the final.
 * Scores live in the matches; this aggregate only links each game to its match, so every decision
 * that needs results receives them as {@code results} (fixture number to games).
 * <ul>
 *     <li>4–5 pairs, one group: final between the first and the second.</li>
 *     <li>6–8 pairs, two groups: semifinals A1×B2 and B1×A2.</li>
 *     <li>9–12 pairs, three groups: the two best group winners go straight to the semifinals;
 *     quarterfinals third winner × runner-up of the second winner's group and runner-up of the
 *     best winner's group × runner-up of the third winner's group.</li>
 *     <li>13–16 pairs, four groups: quarterfinals A1×B2, C1×D2, B1×A2, D1×C2.</li>
 *     <li>17–32 pairs, five to eight groups: round of 16 in a seeded bracket (group winners,
 *     then runners-up, each ranked across the groups); with fewer than 16 qualified, the best
 *     seeds go straight to the quarterfinals.</li>
 * </ul>
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
    public static final int MAX_PLAYERS = 64;

    /**
     * Seeds of a bracket of 16 in playing order: consecutive slots meet in the round of 16, and
     * the two best seeds can only meet in the final.
     */
    private static final int[] BRACKET_OF_16 = {1, 16, 8, 9, 5, 12, 4, 13, 6, 11, 3, 14, 7, 10, 2, 15};

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

    /**
     * Round of a group game inside its group, from 1; null for knockout games. Every round of a
     * group has the same number of games (a group of 3 or 5 pairs has one pair resting each round).
     */
    public Integer groupRound(StageFixture fixture) {
        if (fixture.round() != StageRound.GROUP) {
            return null;
        }
        List<StageFixture> groupGames = fixtures.stream()
                .filter(f -> f.round() == StageRound.GROUP && fixture.group().equals(f.group()))
                .toList();
        long groupSize = pairs.stream().filter(p -> p.group() == fixture.group()).count();
        return groupGames.indexOf(fixture) / (int) (groupSize / 2) + 1;
    }

    public boolean knockoutDrawn() {
        return fixtures.stream().anyMatch(f -> f.round() != StageRound.GROUP);
    }

    /** The final and the third place game, when there is one, have a score: placements are known. */
    public boolean finished() {
        return fixtures.stream().anyMatch(f -> f.round() == StageRound.FINAL && f.scored())
                && fixtures.stream().filter(f -> f.round() == StageRound.THIRD_PLACE).allMatch(StageFixture::scored);
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
                .filter(f -> fixture.round().before(f.round()))
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
     * Draws the next round once every game so far has a score: the knockout after the groups, then
     * each round up to the third place game and the final. Returns this stage when there is
     * nothing to draw.
     */
    public TournamentStage advanced(Map<Integer, FixtureResult> results, Instant now) {
        if (fixtures.isEmpty() || !fixtures.stream().allMatch(f -> f.scored() && results.containsKey(f.number()))) {
            return this;
        }
        List<Game> next = switch (fixtures.getLast().round()) {
            case GROUP -> firstKnockoutRound(results);
            case ROUND_OF_16 -> quarterfinalsAfterRoundOf16(results);
            case QUARTERFINAL -> semifinals(results);
            case SEMIFINAL -> thirdPlaceAndFinal(results);
            case THIRD_PLACE, FINAL -> List.of();
        };
        if (next.isEmpty()) {
            return this;
        }
        List<StageFixture> all = new ArrayList<>(fixtures);
        int number = fixtures.getLast().number();
        for (Game game : next) {
            all.add(new StageFixture(++number, game.round(), null, game.pairA(), game.pairB(), null));
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

    /** Placement of every pair, by pair number; empty until the stage is finished (D-028, D-030). */
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
            switch (f.round()) {
                case ROUND_OF_16 -> placements.put(loser, Placement.ROUND_OF_16);
                case QUARTERFINAL -> placements.put(loser, Placement.QUARTERFINAL);
                // Replaced by the third place game; kept for a final drawn before it existed.
                case SEMIFINAL -> placements.put(loser, Placement.SEMIFINAL);
                case THIRD_PLACE -> {
                    placements.put(winner, Placement.THIRD);
                    placements.put(loser, Placement.FOURTH);
                }
                default -> {
                    placements.put(winner, Placement.CHAMPION);
                    placements.put(loser, Placement.RUNNER_UP);
                }
            }
        }
        return placements;
    }

    static int groupCount(int pairCount) {
        if (pairCount <= 5) {
            return 1;
        }
        return pairCount <= 8 ? 2 : (pairCount + 3) / 4;
    }

    private TournamentStage withFixtures(List<StageFixture> newFixtures, Instant now) {
        return new TournamentStage(id, leagueId, seasonId, date, format, entrants, pairs, newFixtures, createdBy,
                createdAt, now, version);
    }

    /** A knockout game to draw. */
    private record Game(StageRound round, int pairA, int pairB) {
    }

    /** A place of the bracket of 16: a seed waiting in the quarterfinals, or a round of 16 game. */
    private record Slot(int bye, Game game) {
    }

    private List<Game> firstKnockoutRound(Map<Integer, FixtureResult> results) {
        List<List<GroupStanding>> groups = new ArrayList<>(standings(results).values());
        return switch (groups.size()) {
            case 1 -> List.of(game(StageRound.FINAL, groups, 0, 0, 0, 1));
            case 2 -> List.of(game(StageRound.SEMIFINAL, groups, 0, 0, 1, 1),
                    game(StageRound.SEMIFINAL, groups, 1, 0, 0, 1));
            case 3 -> {
                List<List<GroupStanding>> byWinner = groupsByWinner(groups);
                yield List.of(game(StageRound.QUARTERFINAL, byWinner, 2, 0, 1, 1),
                        game(StageRound.QUARTERFINAL, byWinner, 0, 1, 2, 1));
            }
            case 4 -> List.of(game(StageRound.QUARTERFINAL, groups, 0, 0, 1, 1),
                    game(StageRound.QUARTERFINAL, groups, 2, 0, 3, 1),
                    game(StageRound.QUARTERFINAL, groups, 1, 0, 0, 1),
                    game(StageRound.QUARTERFINAL, groups, 3, 0, 2, 1));
            default -> bracketOf16(results).stream().map(Slot::game).filter(Objects::nonNull).toList();
        };
    }

    /** Quarterfinals of a bracket of 16: seeds that waited meet the winners of the round of 16. */
    private List<Game> quarterfinalsAfterRoundOf16(Map<Integer, FixtureResult> results) {
        List<Integer> winners = new ArrayList<>(winnersOf(StageRound.ROUND_OF_16, results));
        List<Integer> places = bracketOf16(results).stream()
                .map(slot -> slot.game() == null ? slot.bye() : winners.removeFirst())
                .toList();
        return IntStream.range(0, places.size() / 2)
                .mapToObj(i -> new Game(StageRound.QUARTERFINAL, places.get(2 * i), places.get(2 * i + 1)))
                .toList();
    }

    /** Semifinals; with three groups, the best two group winners meet the quarterfinal winners. */
    private List<Game> semifinals(Map<Integer, FixtureResult> results) {
        List<Integer> winners = winnersOf(StageRound.QUARTERFINAL, results);
        if (groupCount() != 3) {
            return pairedWinners(StageRound.SEMIFINAL, winners);
        }
        List<List<GroupStanding>> byWinner = groupsByWinner(new ArrayList<>(standings(results).values()));
        return List.of(new Game(StageRound.SEMIFINAL, byWinner.get(0).getFirst().pair(), winners.get(0)),
                new Game(StageRound.SEMIFINAL, byWinner.get(1).getFirst().pair(), winners.get(1)));
    }

    /** Losers of the semifinals play for third place, drawn with the final (D-030). */
    private List<Game> thirdPlaceAndFinal(Map<Integer, FixtureResult> results) {
        List<Integer> winners = winnersOf(StageRound.SEMIFINAL, results);
        List<Integer> losers = losersOf(StageRound.SEMIFINAL, results);
        return List.of(new Game(StageRound.THIRD_PLACE, losers.get(0), losers.get(1)),
                new Game(StageRound.FINAL, winners.get(0), winners.get(1)));
    }

    private static List<Game> pairedWinners(StageRound round, List<Integer> winners) {
        return IntStream.range(0, winners.size() / 2)
                .mapToObj(i -> new Game(round, winners.get(2 * i), winners.get(2 * i + 1)))
                .toList();
    }

    private List<Integer> winnersOf(StageRound round, Map<Integer, FixtureResult> results) {
        return fixtures.stream()
                .filter(f -> f.round() == round)
                .map(f -> results.get(f.number()).pairAWon() ? f.pairA() : f.pairB())
                .toList();
    }

    private List<Integer> losersOf(StageRound round, Map<Integer, FixtureResult> results) {
        return fixtures.stream()
                .filter(f -> f.round() == round)
                .map(f -> results.get(f.number()).pairAWon() ? f.pairB() : f.pairA())
                .toList();
    }

    /**
     * The bracket of 16 of five to eight groups, in playing order. Seeds: group winners, then
     * runners-up, each ranked across the groups; a seed whose opponent would be beyond the
     * qualified pairs waits in the quarterfinals. Two pairs of the same group never meet in the
     * round of 16 when another runner-up can take the place.
     */
    private List<Slot> bracketOf16(Map<Integer, FixtureResult> results) {
        List<List<GroupStanding>> groups = new ArrayList<>(standings(results).values());
        List<GroupStanding> seeds = Stream.concat(
                        groups.stream().map(List::getFirst).sorted(GroupStanding.ACROSS_GROUPS),
                        groups.stream().map(g -> g.get(1)).sorted(GroupStanding.ACROSS_GROUPS))
                .toList();
        List<GroupStanding[]> games = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < BRACKET_OF_16.length; i += 2) {
            GroupStanding top = seeds.get(BRACKET_OF_16[i] - 1);
            int other = BRACKET_OF_16[i + 1];
            if (other > seeds.size()) {
                slots.add(new Slot(top.pair(), null));
            } else {
                games.add(new GroupStanding[]{top, seeds.get(other - 1)});
                slots.add(null);
            }
        }
        separateSameGroup(games);
        List<Slot> bracket = new ArrayList<>();
        int game = 0;
        for (Slot slot : slots) {
            if (slot != null) {
                bracket.add(slot);
            } else {
                GroupStanding[] g = games.get(game++);
                bracket.add(new Slot(0, new Game(StageRound.ROUND_OF_16, g[0].pair(), g[1].pair())));
            }
        }
        return bracket;
    }

    /** Swaps the lower seeds of two round of 16 games while a game puts two pairs of the same group together. */
    private static void separateSameGroup(List<GroupStanding[]> games) {
        for (GroupStanding[] game : games) {
            if (game[0].group() != game[1].group()) {
                continue;
            }
            for (GroupStanding[] other : games) {
                if (other != game && other[0].group() != game[1].group() && game[0].group() != other[1].group()) {
                    GroupStanding swapped = game[1];
                    game[1] = other[1];
                    other[1] = swapped;
                    break;
                }
            }
        }
    }

    private static List<List<GroupStanding>> groupsByWinner(List<List<GroupStanding>> groups) {
        return groups.stream()
                .sorted(Comparator.comparing(List::getFirst, GroupStanding.ACROSS_GROUPS))
                .toList();
    }

    private static Game game(StageRound round, List<List<GroupStanding>> groups, int groupA, int placeA,
                             int groupB, int placeB) {
        return new Game(round, groups.get(groupA).get(placeA).pair(), groups.get(groupB).get(placeB).pair());
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
