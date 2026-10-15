package com.lucchetta.entrypoint.rest.season;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.usecase.season.QuerySeasons;
import com.lucchetta.entrypoint.rest.league.LeagueScope;
import com.lucchetta.entrypoint.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Seasons of a league (members only); the paths without a league act on the "Beach Tennis Arena"
 * league (see {@link LeagueScope}).
 */
@RestController
@RequestMapping({"/api/seasons", "/api/leagues/{leagueId}/seasons"})
public class SeasonController {

    private final CurrentUserProvider currentUserProvider;
    private final LeagueScope leagueScope;
    private final QuerySeasons querySeasons;

    public SeasonController(CurrentUserProvider currentUserProvider, LeagueScope leagueScope,
                            QuerySeasons querySeasons) {
        this.currentUserProvider = currentUserProvider;
        this.leagueScope = leagueScope;
        this.querySeasons = querySeasons;
    }

    @GetMapping
    @Operation(summary = "Lists the league's seasons, most recent first")
    public List<SeasonResponse> list(@PathVariable(required = false) UUID leagueId) {
        AuthenticatedUser user = currentUserProvider.get();
        return querySeasons.list(user, leagueScope.resolve(leagueId, user)).stream()
                .map(SeasonResponse::from)
                .toList();
    }

    @GetMapping("/current")
    @Operation(summary = "Returns the league's active season, or 204 when there is none")
    public ResponseEntity<SeasonResponse> current(@PathVariable(required = false) UUID leagueId) {
        AuthenticatedUser user = currentUserProvider.get();
        return querySeasons.current(user, leagueScope.resolve(leagueId, user))
                .map(view -> ResponseEntity.ok(SeasonResponse.from(view)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Returns one season of the league")
    public SeasonResponse get(@PathVariable(required = false) UUID leagueId, @PathVariable UUID id) {
        AuthenticatedUser user = currentUserProvider.get();
        return SeasonResponse.from(querySeasons.byId(user, leagueScope.resolve(leagueId, user), id));
    }
}
