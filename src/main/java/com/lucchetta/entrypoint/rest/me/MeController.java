package com.lucchetta.entrypoint.rest.me;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.stats.PlayerStats;
import com.lucchetta.core.usecase.player.GetMyStatistics;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;
import com.lucchetta.core.usecase.player.UpdateDisplayName;
import com.lucchetta.entrypoint.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    /**
     * The caller's statistics over every league (decision D-022). No points: each league has its own
     * ranking.
     *
     * @param winRate wins ÷ matches × 100, rounded (`Aproveitamento`); null when there are no matches
     */
    public record GlobalStatsResponse(int matchesPlayed, int wins, int losses, int gamesWon, int gamesLost,
                                      int gameDifference, Integer winRate) {

        static GlobalStatsResponse from(PlayerStats stats) {
            return new GlobalStatsResponse(stats.matchesPlayed(), stats.wins(), stats.losses(), stats.gamesWon(),
                    stats.gamesLost(), stats.gameDifference(), stats.winRate());
        }
    }

    private final CurrentUserProvider currentUserProvider;
    private final GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;
    private final UpdateDisplayName updateDisplayName;
    private final GetMyStatistics getMyStatistics;

    public MeController(CurrentUserProvider currentUserProvider,
                        GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                        UpdateDisplayName updateDisplayName,
                        GetMyStatistics getMyStatistics) {
        this.currentUserProvider = currentUserProvider;
        this.getOrCreateCurrentPlayer = getOrCreateCurrentPlayer;
        this.updateDisplayName = updateDisplayName;
        this.getMyStatistics = getMyStatistics;
    }

    @GetMapping
    @Operation(summary = "Returns the caller's profile, creating it on first access")
    public MeResponse get() {
        AuthenticatedUser user = currentUserProvider.get();
        return MeResponse.from(getOrCreateCurrentPlayer.execute(user), user.admin());
    }

    @PatchMapping
    @Operation(summary = "Updates the caller's display name")
    public MeResponse update(@Valid @RequestBody UpdateMeRequest request) {
        AuthenticatedUser user = currentUserProvider.get();
        return MeResponse.from(updateDisplayName.execute(user, request.displayName()), user.admin());
    }

    @GetMapping("/statistics")
    @Operation(summary = "The caller's own statistics over every league, with win rate (only their own)")
    public GlobalStatsResponse statistics() {
        return GlobalStatsResponse.from(getMyStatistics.execute(currentUserProvider.get()));
    }
}
