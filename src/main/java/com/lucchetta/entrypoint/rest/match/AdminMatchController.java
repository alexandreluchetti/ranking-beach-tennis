package com.lucchetta.entrypoint.rest.match;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.match.Score;
import com.lucchetta.core.usecase.match.admin.MatchAdministration;
import com.lucchetta.entrypoint.rest.league.LeagueScope;
import com.lucchetta.entrypoint.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * League administrator operations on a league's matches. The paths under {@code /api/admin}
 * (system administrator only) act on the "Beach Tennis Arena" league (see {@link LeagueScope}).
 */
@RestController
@RequestMapping({"/api/admin/matches", "/api/leagues/{leagueId}/matches"})
public class AdminMatchController {

    /**
     * Full replacement of the user-provided content.
     *
     * @param version the {@code version} of the match being corrected, as last read
     * @param reason  optional explanation for the participants
     */
    public record CorrectMatchRequest(
            @NotNull Integer version,
            @NotNull OffsetDateTime playedAt,
            @NotNull MatchFormat format,
            @NotNull Integer teamAGames,
            @NotNull Integer teamBGames,
            Integer tiebreakTeamAPoints,
            Integer tiebreakTeamBPoints,
            @NotNull @Size(min = 2, max = 2) List<@NotNull @Valid ParticipantRequest> teamA,
            @NotNull @Size(min = 2, max = 2) List<@NotNull @Valid ParticipantRequest> teamB,
            String reason) {
    }

    public record ReasonRequest(String reason) {
    }

    private final CurrentUserProvider currentUserProvider;
    private final LeagueScope leagueScope;
    private final MatchAdministration administration;

    public AdminMatchController(CurrentUserProvider currentUserProvider, LeagueScope leagueScope,
                                MatchAdministration administration) {
        this.currentUserProvider = currentUserProvider;
        this.leagueScope = leagueScope;
        this.administration = administration;
    }

    @PutMapping("/{id}")
    @Operation(summary = "Corrects a match (league administrator)",
            description = "409 MATCH_MODIFIED when the match changed after it was read; reload and retry.")
    public MatchResponse correct(@PathVariable(required = false) UUID leagueId, @PathVariable UUID id,
                                 @Valid @RequestBody CorrectMatchRequest request) {
        Score score = new Score(request.format(), request.teamAGames(), request.teamBGames(),
                request.tiebreakTeamAPoints(), request.tiebreakTeamBPoints());
        MatchAdministration.Correction correction = new MatchAdministration.Correction(id, request.version(),
                request.playedAt().toInstant(), score,
                MatchRequestMapper.participants(request.teamA(), request.teamB()), request.reason());
        AuthenticatedUser user = currentUserProvider.get();
        return MatchResponse.from(administration.correct(user, leagueScope.resolve(leagueId, user), correction)
                .details());
    }

    @PostMapping("/{id}/void")
    @Operation(summary = "Voids a match; a reason is required (league administrator)")
    public MatchResponse voidMatch(@PathVariable(required = false) UUID leagueId, @PathVariable UUID id,
                                   @RequestBody ReasonRequest request) {
        AuthenticatedUser user = currentUserProvider.get();
        return MatchResponse.from(administration.voidMatch(user, leagueScope.resolve(leagueId, user), id,
                request.reason()).details());
    }

    @PostMapping("/{id}/revert-void")
    @Operation(summary = "Restores a voided match; a reason is required (league administrator)")
    public MatchResponse revertVoid(@PathVariable(required = false) UUID leagueId, @PathVariable UUID id,
                                    @RequestBody ReasonRequest request) {
        AuthenticatedUser user = currentUserProvider.get();
        return MatchResponse.from(administration.revertVoid(user, leagueScope.resolve(leagueId, user), id,
                request.reason()).details());
    }
}
