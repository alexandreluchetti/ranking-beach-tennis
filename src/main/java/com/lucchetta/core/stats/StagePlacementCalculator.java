package com.lucchetta.core.stats;

import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.tournament.FixtureResult;
import com.lucchetta.core.domain.tournament.Placement;
import com.lucchetta.core.domain.tournament.StageEntrant;
import com.lucchetta.core.domain.tournament.StageFixture;
import com.lucchetta.core.domain.tournament.StagePair;
import com.lucchetta.core.domain.tournament.StageRound;
import com.lucchetta.core.domain.tournament.TournamentStage;
import com.lucchetta.core.usecase.match.MatchDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Points of a daily tournament season (D-028): each finished stage (its final and third place game
 * have a score, D-030) gives every registered player the points of their pair's placement, from
 * the season's table. Guests get nothing (D-004). Results come from the stage matches, so a
 * correction or a cleared score is reflected at once (D-007).
 */
public final class StagePlacementCalculator {

    /**
     * @param points     placement points of each player
     * @param records    stages, titles and finals of each player
     * @param reachedAt  when each player with points reached their current total: the last game
     *                   (final or third place) of the last stage that gave them points
     */
    public record Result(Map<UUID, Integer> points, Map<UUID, TournamentRecord> records,
                         Map<UUID, Instant> reachedAt) {
    }

    private StagePlacementCalculator() {
    }

    /**
     * @param stages  the season's stages
     * @param matches matches of the season's league (at least the stage matches)
     */
    public static Result byPlayer(Season season, Collection<TournamentStage> stages,
                                  Collection<MatchDetails> matches) {
        Map<UUID, Match> byId = matches.stream()
                .map(MatchDetails::match)
                .filter(m -> m.stageMatch() && m.status() == MatchStatus.VALID)
                .collect(Collectors.toMap(Match::id, Function.identity()));
        Map<UUID, Integer> points = new HashMap<>();
        Map<UUID, TournamentRecord> records = new HashMap<>();
        Map<UUID, Instant> reachedAt = new HashMap<>();
        List<TournamentStage> inOrder = stages.stream()
                .filter(s -> s.seasonId().equals(season.id()))
                .sorted(Comparator.comparing(TournamentStage::date))
                .toList();
        for (TournamentStage stage : inOrder) {
            Map<Integer, FixtureResult> results = new HashMap<>();
            Instant finalPlayedAt = null;
            for (StageFixture fixture : stage.fixtures()) {
                Match match = fixture.scored() ? byId.get(fixture.matchId()) : null;
                if (match != null) {
                    results.put(fixture.number(),
                            new FixtureResult(match.score().teamAGames(), match.score().teamBGames()));
                    boolean lastLevel = fixture.round() == StageRound.FINAL
                            || fixture.round() == StageRound.THIRD_PLACE;
                    if (lastLevel && (finalPlayedAt == null || match.playedAt().isAfter(finalPlayedAt))) {
                        finalPlayedAt = match.playedAt();
                    }
                }
            }
            Map<Integer, Placement> placements = stage.placements(results);
            if (placements.isEmpty() || finalPlayedAt == null) {
                continue; // the stage is not finished
            }
            for (Map.Entry<Integer, Placement> entry : placements.entrySet()) {
                StagePair pair = stage.pair(entry.getKey());
                Placement placement = entry.getValue();
                int earned = season.placementPoints().pointsFor(placement);
                for (int position : List.of(pair.firstPosition(), pair.secondPosition())) {
                    StageEntrant entrant = stage.entrant(position);
                    if (entrant.guest()) {
                        continue;
                    }
                    UUID player = entrant.playerId();
                    points.merge(player, earned, Integer::sum);
                    records.merge(player, TournamentRecord.EMPTY.plus(placement == Placement.CHAMPION,
                            placement == Placement.CHAMPION || placement == Placement.RUNNER_UP),
                            TournamentRecord::sum);
                    if (earned > 0) {
                        reachedAt.put(player, finalPlayedAt);
                    }
                }
            }
        }
        return new Result(points, records, reachedAt);
    }
}
