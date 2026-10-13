package com.lucchetta.entrypoint.rest.season;

import com.lucchetta.core.usecase.season.QuerySeasons;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/seasons")
public class SeasonController {

    private final QuerySeasons querySeasons;

    public SeasonController(QuerySeasons querySeasons) {
        this.querySeasons = querySeasons;
    }

    @GetMapping
    @Operation(summary = "Lists all seasons, most recent first")
    public List<SeasonResponse> list() {
        return querySeasons.list().stream().map(SeasonResponse::from).toList();
    }

    @GetMapping("/current")
    @Operation(summary = "Returns the active season, or 204 when there is none")
    public ResponseEntity<SeasonResponse> current() {
        return querySeasons.current()
                .map(view -> ResponseEntity.ok(SeasonResponse.from(view)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Returns one season")
    public SeasonResponse get(@PathVariable UUID id) {
        return SeasonResponse.from(querySeasons.byId(id));
    }
}
