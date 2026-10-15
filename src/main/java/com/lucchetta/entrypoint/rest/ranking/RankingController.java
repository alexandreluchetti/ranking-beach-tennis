package com.lucchetta.entrypoint.rest.ranking;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.usecase.ranking.GetSeasonRanking;
import com.lucchetta.entrypoint.rest.league.LeagueScope;
import com.lucchetta.entrypoint.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Season rankings of a league (members only); the paths without a league act on the "Beach
 * Tennis Arena" league (see {@link LeagueScope}).
 */
@RestController
@RequestMapping({"/api/seasons", "/api/leagues/{leagueId}/seasons"})
public class RankingController {

    private final CurrentUserProvider currentUserProvider;
    private final LeagueScope leagueScope;
    private final GetSeasonRanking getSeasonRanking;

    public RankingController(CurrentUserProvider currentUserProvider, LeagueScope leagueScope,
                             GetSeasonRanking getSeasonRanking) {
        this.currentUserProvider = currentUserProvider;
        this.leagueScope = leagueScope;
        this.getSeasonRanking = getSeasonRanking;
    }

    @GetMapping("/current/ranking")
    @Operation(summary = "Ranking of the league's active season, or 204 when there is none")
    public ResponseEntity<RankingResponse> current(@PathVariable(required = false) UUID leagueId) {
        AuthenticatedUser user = currentUserProvider.get();
        return getSeasonRanking.current(user, leagueScope.resolve(leagueId, user))
                .map(ranking -> ResponseEntity.ok(RankingResponse.from(ranking)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}/ranking")
    @Operation(summary = "Ranking of any season of the league")
    public RankingResponse bySeason(@PathVariable(required = false) UUID leagueId, @PathVariable UUID id) {
        AuthenticatedUser user = currentUserProvider.get();
        return RankingResponse.from(getSeasonRanking.bySeasonId(user, leagueScope.resolve(leagueId, user), id));
    }
}
