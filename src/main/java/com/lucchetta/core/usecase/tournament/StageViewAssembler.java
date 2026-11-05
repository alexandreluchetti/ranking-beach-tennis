package com.lucchetta.core.usecase.tournament;

import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.tournament.FixtureResult;
import com.lucchetta.core.domain.tournament.StageEntrant;
import com.lucchetta.core.domain.tournament.StageFixture;
import com.lucchetta.core.domain.tournament.TournamentStage;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.match.MatchDetails;
import com.lucchetta.core.usecase.match.MatchDetailsAssembler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds stage views and the game results the stage rules need, from the stage matches.
 */
public class StageViewAssembler {

    private final MatchRepository matchRepository;
    private final MatchDetailsAssembler matchDetailsAssembler;
    private final PlayerRepository playerRepository;

    public StageViewAssembler(MatchRepository matchRepository,
                              MatchDetailsAssembler matchDetailsAssembler,
                              PlayerRepository playerRepository) {
        this.matchRepository = matchRepository;
        this.matchDetailsAssembler = matchDetailsAssembler;
        this.playerRepository = playerRepository;
    }

    /** Games of the scored fixtures, by fixture number. */
    public Map<Integer, FixtureResult> results(TournamentStage stage) {
        return results(linkedMatches(stage));
    }

    public StageView view(TournamentStage stage, LeagueAccess.Context context) {
        Map<Integer, Match> matches = linkedMatches(stage);
        Map<Integer, FixtureResult> results = results(matches);
        List<Integer> numbers = List.copyOf(matches.keySet());
        List<MatchDetails> details = matchDetailsAssembler.assembleAll(numbers.stream().map(matches::get).toList());
        Map<Integer, MatchDetails> byFixture = new TreeMap<>();
        for (int i = 0; i < numbers.size(); i++) {
            byFixture.put(numbers.get(i), details.get(i));
        }
        Map<UUID, String> names = playerRepository.findAllByIds(stage.entrants().stream()
                        .map(StageEntrant::playerId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Player::id, p -> p.displayName().value()));
        boolean canEnterScores = context.canAdminister()
                || (context.member() && context.league().allowsScoreEntryBy(false));
        return new StageView(stage, byFixture, results, stage.standings(results), stage.placements(results), names,
                canEnterScores && !context.league().archived(),
                context.canAdminister() && !context.league().archived());
    }

    /** The valid match of each scored fixture, by fixture number. */
    private Map<Integer, Match> linkedMatches(TournamentStage stage) {
        Map<UUID, Match> byId = matchRepository.findByStage(stage.id()).stream()
                .collect(Collectors.toMap(Match::id, Function.identity()));
        Map<Integer, Match> linked = new TreeMap<>();
        for (StageFixture fixture : stage.fixtures()) {
            Match match = fixture.scored() ? byId.get(fixture.matchId()) : null;
            if (match != null && match.status() == MatchStatus.VALID) {
                linked.put(fixture.number(), match);
            }
        }
        return linked;
    }

    private static Map<Integer, FixtureResult> results(Map<Integer, Match> matches) {
        Map<Integer, FixtureResult> results = new HashMap<>();
        matches.forEach((number, match) ->
                results.put(number, new FixtureResult(match.score().teamAGames(), match.score().teamBGames())));
        return results;
    }
}
