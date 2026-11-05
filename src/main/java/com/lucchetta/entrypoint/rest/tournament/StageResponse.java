package com.lucchetta.entrypoint.rest.tournament;

import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.match.Score;
import com.lucchetta.core.domain.tournament.GroupStanding;
import com.lucchetta.core.domain.tournament.Placement;
import com.lucchetta.core.domain.tournament.StageFixture;
import com.lucchetta.core.domain.tournament.StageRound;
import com.lucchetta.core.domain.tournament.TournamentStage;
import com.lucchetta.core.usecase.match.MatchDetails;
import com.lucchetta.core.usecase.tournament.StageView;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A stage of a daily tournament (D-027) with its players, pairs, group standings and games.
 *
 * @param knockoutDrawn  the groups are closed: group scores change only after reopening them
 * @param finished       the final has a score; every pair has its {@code placement}
 * @param canEnterScores whether the caller may enter the score of a game without one (D-029)
 * @param canAdminister  whether the caller may draw again, correct or clear scores, reopen the
 *                       groups or delete the stage
 */
public record StageResponse(
        UUID id,
        UUID seasonId,
        LocalDate date,
        MatchFormat format,
        boolean knockoutDrawn,
        boolean finished,
        boolean canEnterScores,
        boolean canAdminister,
        List<PlayerResponse> players,
        List<PairResponse> pairs,
        List<GroupResponse> groups,
        List<FixtureResponse> fixtures) {

    /** A member ({@code playerId}, {@code displayName}) or a guest ({@code guestName}). */
    public record PlayerResponse(int position, UUID playerId, String displayName, String guestName) {
    }

    /**
     * @param players   positions of the two players
     * @param placement final placement, null until the final has a score (D-028)
     */
    public record PairResponse(int number, int group, List<Integer> players, Placement placement) {
    }

    /** @param standings best first: wins, game difference, games won, draw order */
    public record GroupResponse(int group, List<StandingResponse> standings) {
    }

    public record StandingResponse(int pair, int played, int wins, int gamesWon, int gamesLost, int gameDifference) {

        static StandingResponse from(GroupStanding s) {
            return new StandingResponse(s.pair(), s.played(), s.wins(), s.gamesWon(), s.gamesLost(),
                    s.gameDifference());
        }
    }

    /**
     * Pair A plays as team A.
     *
     * @param group null for knockout games
     * @param score null while the game has no score
     */
    public record FixtureResponse(int number, StageRound round, Integer group, int pairA, int pairB,
                                  ScoreResponse score) {
    }

    /** @param matchVersion send it back as {@code expectedMatchVersion} when correcting */
    public record ScoreResponse(UUID matchId, int matchVersion, int teamAGames, int teamBGames,
                                Integer tiebreakTeamAPoints, Integer tiebreakTeamBPoints) {

        static ScoreResponse from(MatchDetails details) {
            Score score = details.match().score();
            return new ScoreResponse(details.match().id(), details.match().version(), score.teamAGames(),
                    score.teamBGames(), score.tiebreakTeamAPoints(), score.tiebreakTeamBPoints());
        }
    }

    public static StageResponse from(StageView view) {
        TournamentStage stage = view.stage();
        return new StageResponse(stage.id(), stage.seasonId(), stage.date(), stage.format(), stage.knockoutDrawn(),
                stage.finished(), view.canEnterScores(), view.canAdminister(),
                stage.entrants().stream()
                        .map(e -> new PlayerResponse(e.position(), e.playerId(),
                                e.guest() ? null : view.playerNames().get(e.playerId()), e.guestName()))
                        .toList(),
                stage.pairs().stream()
                        .map(p -> new PairResponse(p.number(), p.group(),
                                List.of(p.firstPosition(), p.secondPosition()), view.placements().get(p.number())))
                        .toList(),
                view.standings().entrySet().stream()
                        .map(g -> new GroupResponse(g.getKey(), g.getValue().stream().map(StandingResponse::from)
                                .toList()))
                        .toList(),
                stage.fixtures().stream().map(f -> fixture(f, view)).toList());
    }

    private static FixtureResponse fixture(StageFixture fixture, StageView view) {
        MatchDetails details = view.matches().get(fixture.number());
        return new FixtureResponse(fixture.number(), fixture.round(), fixture.group(), fixture.pairA(),
                fixture.pairB(), details == null ? null : ScoreResponse.from(details));
    }
}
