package com.lucchetta.entrypoint.rest.tournament;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.tournament.TournamentStage;
import com.lucchetta.core.usecase.tournament.ManageStages;
import com.lucchetta.core.usecase.tournament.QueryStages;
import com.lucchetta.core.usecase.tournament.StageScores;
import com.lucchetta.entrypoint.rest.tournament.StageRequests.ClearScoreRequest;
import com.lucchetta.entrypoint.rest.tournament.StageRequests.CreateStageRequest;
import com.lucchetta.entrypoint.rest.tournament.StageRequests.RedrawRequest;
import com.lucchetta.entrypoint.rest.tournament.StageRequests.ScoreRequest;
import com.lucchetta.entrypoint.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Stages of a daily tournament season (D-027): members read them; league administrators open,
 * draw again, reopen the groups and delete; scores follow the league setting (D-029).
 */
@RestController
@RequestMapping("/api/leagues/{leagueId}/stages")
public class StageController {

    /** @param finished the final has a score */
    public record StageSummaryResponse(UUID id, UUID seasonId, LocalDate date, MatchFormat format, int playerCount,
                                       boolean knockoutDrawn, boolean finished) {

        static StageSummaryResponse from(TournamentStage stage) {
            return new StageSummaryResponse(stage.id(), stage.seasonId(), stage.date(), stage.format(),
                    stage.entrants().size(), stage.knockoutDrawn(), stage.finished());
        }
    }

    private final CurrentUserProvider currentUserProvider;
    private final QueryStages queryStages;
    private final ManageStages manageStages;
    private final StageScores stageScores;

    public StageController(CurrentUserProvider currentUserProvider, QueryStages queryStages,
                           ManageStages manageStages, StageScores stageScores) {
        this.currentUserProvider = currentUserProvider;
        this.queryStages = queryStages;
        this.manageStages = manageStages;
        this.stageScores = stageScores;
    }

    @GetMapping
    @Operation(summary = "Stages of a season, by date; without seasonId, of the active season")
    public List<StageSummaryResponse> list(@PathVariable UUID leagueId,
                                           @RequestParam(required = false) UUID seasonId) {
        return queryStages.list(currentUserProvider.get(), leagueId, seasonId).stream()
                .map(StageSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One stage with its players, pairs, group standings and games")
    public StageResponse get(@PathVariable UUID leagueId, @PathVariable UUID id) {
        return StageResponse.from(queryStages.get(currentUserProvider.get(), leagueId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Opens the stage of a playing day and draws pairs and groups (league administrator)")
    public StageResponse create(@PathVariable UUID leagueId, @Valid @RequestBody CreateStageRequest request) {
        AuthenticatedUser user = currentUserProvider.get();
        return StageResponse.from(manageStages.create(user, leagueId, new ManageStages.Setup(request.date(),
                request.format(), StageRequests.attendees(request.players()))));
    }

    @PostMapping("/{id}/draw")
    @Operation(summary = "Draws again, possibly with new players or format, until the first score")
    public StageResponse redraw(@PathVariable UUID leagueId, @PathVariable UUID id,
                                @Valid @RequestBody RedrawRequest request) {
        return StageResponse.from(manageStages.redraw(currentUserProvider.get(), leagueId, id,
                new ManageStages.Redraw(request.format(), StageRequests.attendees(request.players()))));
    }

    @PostMapping("/{id}/reopen-groups")
    @Operation(summary = "Removes the knockout so group scores can be corrected, before any knockout score")
    public StageResponse reopenGroups(@PathVariable UUID leagueId, @PathVariable UUID id) {
        return StageResponse.from(manageStages.reopenGroups(currentUserProvider.get(), leagueId, id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deletes a stage that never had a score (league administrator)")
    public void delete(@PathVariable UUID leagueId, @PathVariable UUID id) {
        manageStages.delete(currentUserProvider.get(), leagueId, id);
    }

    @PutMapping("/{id}/fixtures/{number}/score")
    @Operation(summary = "Enters the score of a game, or corrects it (league administrator)")
    public StageResponse score(@PathVariable UUID leagueId, @PathVariable UUID id, @PathVariable int number,
                               @Valid @RequestBody ScoreRequest request) {
        return StageResponse.from(stageScores.record(currentUserProvider.get(), leagueId, id, number,
                new StageScores.Entry(request.teamAGames(), request.teamBGames(), request.tiebreakTeamAPoints(),
                        request.tiebreakTeamBPoints(), request.expectedMatchVersion(), request.reason())));
    }

    @PostMapping("/{id}/fixtures/{number}/clear")
    @Operation(summary = "Removes the score of a game, voiding its match (league administrator)")
    public StageResponse clear(@PathVariable UUID leagueId, @PathVariable UUID id, @PathVariable int number,
                               @RequestBody ClearScoreRequest request) {
        return StageResponse.from(stageScores.clear(currentUserProvider.get(), leagueId, id, number,
                request.reason()));
    }
}
