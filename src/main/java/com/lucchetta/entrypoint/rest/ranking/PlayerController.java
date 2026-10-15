package com.lucchetta.entrypoint.rest.ranking;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.usecase.player.QueryPlayers;
import com.lucchetta.core.usecase.ranking.GetPlayerProfile;
import com.lucchetta.core.usecase.ranking.SeasonStanding;
import com.lucchetta.entrypoint.rest.league.LeagueScope;
import com.lucchetta.entrypoint.rest.season.SeasonResponse;
import com.lucchetta.entrypoint.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Players of a league (members only): users who are not members are not visible inside it. The
 * paths without a league act on the "Beach Tennis Arena" league (see {@link LeagueScope}).
 */
@RestController
@RequestMapping({"/api/players", "/api/leagues/{leagueId}/players"})
public class PlayerController {

    public record PlayerSummary(UUID id, String displayName) {
    }

    public record StandingResponse(SeasonResponse season, int position, int rankedPlayers, StatsResponse stats) {

        static StandingResponse from(SeasonStanding standing) {
            return standing == null ? null : new StandingResponse(SeasonResponse.from(standing.season()),
                    standing.position(), standing.rankedPlayers(), StatsResponse.from(standing.stats()));
        }
    }

    /**
     * @param currentSeason null when there is no active season
     */
    public record ProfileResponse(UUID id, String displayName, StandingResponse currentSeason, StatsResponse career,
                                  List<StandingResponse> pastSeasons) {
    }

    private final CurrentUserProvider currentUserProvider;
    private final LeagueScope leagueScope;
    private final QueryPlayers queryPlayers;
    private final GetPlayerProfile getPlayerProfile;

    public PlayerController(CurrentUserProvider currentUserProvider, LeagueScope leagueScope,
                            QueryPlayers queryPlayers, GetPlayerProfile getPlayerProfile) {
        this.currentUserProvider = currentUserProvider;
        this.leagueScope = leagueScope;
        this.queryPlayers = queryPlayers;
        this.getPlayerProfile = getPlayerProfile;
    }

    @GetMapping
    @Operation(summary = "Lists the league's members by name")
    public List<PlayerSummary> list(@PathVariable(required = false) UUID leagueId) {
        AuthenticatedUser user = currentUserProvider.get();
        return queryPlayers.list(user, leagueScope.resolve(leagueId, user)).stream()
                .map(p -> new PlayerSummary(p.id(), p.displayName().value()))
                .toList();
    }

    @GetMapping("/{id}/profile")
    @Operation(summary = "Player profile in the league: current season, league statistics and past seasons",
            description = "The player's matches come from GET /api/leagues/{leagueId}/matches?playerId=...")
    public ProfileResponse profile(@PathVariable(required = false) UUID leagueId, @PathVariable UUID id) {
        AuthenticatedUser user = currentUserProvider.get();
        GetPlayerProfile.PlayerProfile profile = getPlayerProfile.execute(user, leagueScope.resolve(leagueId, user),
                id);
        return new ProfileResponse(profile.player().id(), profile.player().displayName().value(),
                StandingResponse.from(profile.currentSeason()), StatsResponse.from(profile.career()),
                profile.pastSeasons().stream().map(StandingResponse::from).toList());
    }
}
