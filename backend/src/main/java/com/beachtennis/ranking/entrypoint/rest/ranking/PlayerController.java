package com.beachtennis.ranking.entrypoint.rest.ranking;

import com.beachtennis.ranking.core.usecase.player.QueryPlayers;
import com.beachtennis.ranking.core.usecase.ranking.GetPlayerProfile;
import com.beachtennis.ranking.core.usecase.ranking.SeasonStanding;
import com.beachtennis.ranking.entrypoint.rest.season.SeasonResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/players")
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

    private final QueryPlayers queryPlayers;
    private final GetPlayerProfile getPlayerProfile;

    public PlayerController(QueryPlayers queryPlayers, GetPlayerProfile getPlayerProfile) {
        this.queryPlayers = queryPlayers;
        this.getPlayerProfile = getPlayerProfile;
    }

    @GetMapping
    @Operation(summary = "Lists registered players by name")
    public List<PlayerSummary> list() {
        return queryPlayers.list().stream()
                .map(p -> new PlayerSummary(p.id(), p.displayName().value()))
                .toList();
    }

    @GetMapping("/{id}/profile")
    @Operation(summary = "Player profile: current season, career and past seasons",
            description = "The player's matches come from GET /api/matches?playerId=...")
    public ProfileResponse profile(@PathVariable UUID id) {
        GetPlayerProfile.PlayerProfile profile = getPlayerProfile.execute(id);
        return new ProfileResponse(profile.player().id(), profile.player().displayName().value(),
                StandingResponse.from(profile.currentSeason()), StatsResponse.from(profile.career()),
                profile.pastSeasons().stream().map(StandingResponse::from).toList());
    }
}
