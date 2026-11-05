package com.lucchetta.entrypoint.rest.season;

import com.lucchetta.core.domain.PlacementPoints;
import jakarta.validation.constraints.NotNull;

/** Points per placement in a daily tournament stage (D-028). */
public record PlacementPointsDto(@NotNull Integer champion, @NotNull Integer runnerUp, @NotNull Integer semifinal,
                                 @NotNull Integer quarterfinal, @NotNull Integer groupStage) {

    public PlacementPoints toDomain() {
        return new PlacementPoints(champion, runnerUp, semifinal, quarterfinal, groupStage);
    }

    static PlacementPoints toDomain(PlacementPointsDto dto) {
        return dto == null ? null : dto.toDomain();
    }

    static PlacementPointsDto from(PlacementPoints points) {
        return points == null ? null : new PlacementPointsDto(points.champion(), points.runnerUp(),
                points.semifinal(), points.quarterfinal(), points.groupStage());
    }
}
