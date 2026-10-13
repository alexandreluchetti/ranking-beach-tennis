package com.lucchetta.core.usecase.season;

import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.port.SeasonRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only season queries available to every authenticated user.
 */
public class QuerySeasons {

    private final SeasonRepository seasonRepository;
    private final Clock clock;

    public QuerySeasons(SeasonRepository seasonRepository, Clock clock) {
        this.seasonRepository = seasonRepository;
        this.clock = clock;
    }

    public List<SeasonView> list() {
        LocalDate today = BusinessCalendar.today(clock);
        return seasonRepository.findAll().stream()
                .map(season -> new SeasonView(season, season.statusOn(today)))
                .toList();
    }

    public SeasonView byId(UUID id) {
        LocalDate today = BusinessCalendar.today(clock);
        return seasonRepository.findById(id)
                .map(season -> new SeasonView(season, season.statusOn(today)))
                .orElseThrow(() -> new NotFoundException("SEASON_NOT_FOUND"));
    }

    /** The active season, if there is one today. */
    public Optional<SeasonView> current() {
        LocalDate today = BusinessCalendar.today(clock);
        return seasonRepository.findContaining(today)
                .map(season -> new SeasonView(season, season.statusOn(today)));
    }
}
