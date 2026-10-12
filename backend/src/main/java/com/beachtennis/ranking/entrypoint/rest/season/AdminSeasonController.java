package com.beachtennis.ranking.entrypoint.rest.season;

import com.beachtennis.ranking.core.usecase.season.CreateSeason;
import com.beachtennis.ranking.core.usecase.season.UpdateSeason;
import com.beachtennis.ranking.entrypoint.security.CurrentUserProvider;
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

@RestController
@RequestMapping("/api/admin/seasons")
public class AdminSeasonController {

    private final CurrentUserProvider currentUserProvider;
    private final CreateSeason createSeason;
    private final UpdateSeason updateSeason;

    public AdminSeasonController(CurrentUserProvider currentUserProvider,
                                 CreateSeason createSeason,
                                 UpdateSeason updateSeason) {
        this.currentUserProvider = currentUserProvider;
        this.createSeason = createSeason;
        this.updateSeason = updateSeason;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creates a season (admin)")
    public SeasonResponse create(@Valid @RequestBody CreateSeasonRequest request) {
        return SeasonResponse.from(createSeason.execute(currentUserProvider.get(),
                new CreateSeason.Command(request.name(), request.startDate(), request.endDate())));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Changes a season that is not closed (admin)")
    public SeasonResponse update(@PathVariable UUID id, @RequestBody UpdateSeasonRequest request) {
        return SeasonResponse.from(updateSeason.execute(currentUserProvider.get(), id,
                new UpdateSeason.Command(request.name(), request.startDate(), request.endDate())));
    }
}
