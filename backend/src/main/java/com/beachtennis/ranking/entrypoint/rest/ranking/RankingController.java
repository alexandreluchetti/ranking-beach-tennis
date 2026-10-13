package com.beachtennis.ranking.entrypoint.rest.ranking;

import com.beachtennis.ranking.core.usecase.ranking.GetSeasonRanking;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/seasons")
public class RankingController {

    private final GetSeasonRanking getSeasonRanking;

    public RankingController(GetSeasonRanking getSeasonRanking) {
        this.getSeasonRanking = getSeasonRanking;
    }

    @GetMapping("/current/ranking")
    @Operation(summary = "Ranking of the active season, or 204 when there is none")
    public ResponseEntity<RankingResponse> current() {
        return getSeasonRanking.current()
                .map(ranking -> ResponseEntity.ok(RankingResponse.from(ranking)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}/ranking")
    @Operation(summary = "Ranking of any season")
    public RankingResponse bySeason(@PathVariable UUID id) {
        return RankingResponse.from(getSeasonRanking.bySeasonId(id));
    }
}
