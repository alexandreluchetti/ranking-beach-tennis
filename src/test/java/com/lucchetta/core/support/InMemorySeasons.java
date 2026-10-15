package com.lucchetta.core.support;

import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.SeasonAuditEntry;
import com.lucchetta.core.port.SeasonAuditLog;
import com.lucchetta.core.port.SeasonRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory season repository and audit log for use case unit tests. */
public class InMemorySeasons implements SeasonRepository, SeasonAuditLog {

    private final Map<UUID, Season> seasons = new LinkedHashMap<>();
    private final List<SeasonAuditEntry> audit = new ArrayList<>();

    public Season add(Season season) {
        seasons.put(season.id(), season);
        return season;
    }

    public List<SeasonAuditEntry> audit() {
        return audit;
    }

    @Override
    public List<Season> findAll(UUID leagueId) {
        return seasons.values().stream()
                .filter(s -> s.leagueId().equals(leagueId))
                .sorted(Comparator.comparing(Season::startDate).reversed())
                .toList();
    }

    @Override
    public Optional<Season> findById(UUID id) {
        return Optional.ofNullable(seasons.get(id));
    }

    @Override
    public Optional<Season> findContaining(UUID leagueId, LocalDate date) {
        return seasons.values().stream()
                .filter(s -> s.leagueId().equals(leagueId))
                .filter(s -> !date.isBefore(s.startDate()) && !date.isAfter(s.endDate()))
                .findFirst();
    }

    @Override
    public boolean existsOverlapping(UUID leagueId, LocalDate startDate, LocalDate endDate, UUID excludedId) {
        return seasons.values().stream()
                .filter(s -> s.leagueId().equals(leagueId))
                .filter(s -> !s.id().equals(excludedId))
                .anyMatch(s -> s.overlaps(startDate, endDate));
    }

    @Override
    public Season insert(Season season) {
        return add(season);
    }

    @Override
    public Season update(Season season) {
        return add(season);
    }

    @Override
    public void record(SeasonAuditEntry entry) {
        audit.add(entry);
    }
}
