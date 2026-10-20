package com.lucchetta.entrypoint.rest.season;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.usecase.season.CreateSeason;
import com.lucchetta.core.usecase.season.UpdateSeason;
import com.lucchetta.entrypoint.rest.league.LeagueScope;
import com.lucchetta.entrypoint.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Season management of a league (league administrator). The paths under {@code /api/admin}
 * (system administrator only) act on the "Beach Tennis Arena" league (see {@link LeagueScope}).
 */
@RestController
@RequestMapping({"/api/admin/seasons", "/api/leagues/{leagueId}/seasons"})
public class AdminSeasonController {

    private final CurrentUserProvider currentUserProvider;
    private final LeagueScope leagueScope;
    private final CreateSeason createSeason;
    private final UpdateSeason updateSeason;

    public AdminSeasonController(CurrentUserProvider currentUserProvider,
                                 LeagueScope leagueScope,
                                 CreateSeason createSeason,
                                 UpdateSeason updateSeason) {
        this.currentUserProvider = currentUserProvider;
        this.leagueScope = leagueScope;
        this.createSeason = createSeason;
        this.updateSeason = updateSeason;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creates a season (league administrator)")
    public SeasonResponse create(@PathVariable(required = false) UUID leagueId,
                                 @Valid @RequestBody CreateSeasonRequest request) {
        AuthenticatedUser user = currentUserProvider.get();
        return SeasonResponse.from(createSeason.execute(user, leagueScope.resolve(leagueId, user),
                new CreateSeason.Command(request.name(), request.startDate(), request.endDate(),
                        request.playingDays())));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Changes a season that is not closed (league administrator)")
    public SeasonResponse update(@PathVariable(required = false) UUID leagueId, @PathVariable UUID id,
                                 @Valid @RequestBody UpdateSeasonRequest request) {
        AuthenticatedUser user = currentUserProvider.get();
        return SeasonResponse.from(updateSeason.execute(user, leagueScope.resolve(leagueId, user), id,
                new UpdateSeason.Command(request.name(), request.startDate(), request.endDate(),
                        request.playingDays())));
    }
}
