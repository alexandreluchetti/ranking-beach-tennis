package com.lucchetta.core.usecase.season;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.port.SeasonRepository;
import com.lucchetta.core.usecase.league.LeagueAccess;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Seasons of a league. The methods taking the caller are for the league's members (decision
 * D-020); the others serve use cases that already checked access.
 */
public class QuerySeasons {

    private final LeagueAccess leagueAccess;
    private final SeasonRepository seasonRepository;
    private final Clock clock;

    public QuerySeasons(LeagueAccess leagueAccess, SeasonRepository seasonRepository, Clock clock) {
        this.leagueAccess = leagueAccess;
        this.seasonRepository = seasonRepository;
        this.clock = clock;
    }

    public List<SeasonView> list(AuthenticatedUser user, UUID leagueId) {
        leagueAccess.requireMember(user, leagueId);
        return list(leagueId);
    }

    public SeasonView byId(AuthenticatedUser user, UUID leagueId, UUID seasonId) {
        leagueAccess.requireMember(user, leagueId);
        return byId(leagueId, seasonId);
    }

    /** The league's active season, if there is one today. */
    public Optional<SeasonView> current(AuthenticatedUser user, UUID leagueId) {
        leagueAccess.requireMember(user, leagueId);
        return current(leagueId);
    }

    /** Seasons of the league, most recent first. No access check. */
    public List<SeasonView> list(UUID leagueId) {
        LocalDate today = BusinessCalendar.today(clock);
        return seasonRepository.findAll(leagueId).stream()
                .map(season -> new SeasonView(season, season.statusOn(today)))
                .toList();
    }

    /** No access check. */
    public SeasonView byId(UUID leagueId, UUID seasonId) {
        LocalDate today = BusinessCalendar.today(clock);
        return seasonRepository.findById(seasonId)
                .filter(season -> season.leagueId().equals(leagueId))
                .map(season -> new SeasonView(season, season.statusOn(today)))
                .orElseThrow(() -> new NotFoundException("SEASON_NOT_FOUND"));
    }

    /** No access check. */
    public Optional<SeasonView> current(UUID leagueId) {
        LocalDate today = BusinessCalendar.today(clock);
        return seasonRepository.findContaining(leagueId, today)
                .map(season -> new SeasonView(season, season.statusOn(today)));
    }

    /** The league's season covering the given season id, used to check ownership. */
    static Season requireInLeague(SeasonRepository seasonRepository, UUID leagueId, UUID seasonId) {
        return seasonRepository.findById(seasonId)
                .filter(season -> season.leagueId().equals(leagueId))
                .orElseThrow(() -> new NotFoundException("SEASON_NOT_FOUND"));
    }
}
