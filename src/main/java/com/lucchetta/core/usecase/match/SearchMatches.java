package com.lucchetta.core.usecase.match;

import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.usecase.season.QuerySeasons;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Match history, most recent first, with combinable filters.
 */
public class SearchMatches {

    public static final int MAX_PAGE_SIZE = 100;

    /**
     * All fields are optional; an empty {@code outcomes} means every outcome. Dates are
     * inclusive business dates (Brasília).
     */
    public record Criteria(UUID seasonId, UUID playerId, LocalDate from, LocalDate to, Set<MatchOutcome> outcomes,
                           int page, int size) {

        public Criteria {
            outcomes = outcomes == null || outcomes.isEmpty() ? EnumSet.allOf(MatchOutcome.class) : outcomes;
            page = Math.max(page, 0);
            size = Math.clamp(size, 1, MAX_PAGE_SIZE);
        }
    }

    public record Page(List<MatchDetails> items, int page, int size, boolean hasNext) {
    }

    private final MatchRepository matchRepository;
    private final QuerySeasons querySeasons;
    private final MatchDetailsAssembler assembler;

    public SearchMatches(MatchRepository matchRepository, QuerySeasons querySeasons, MatchDetailsAssembler assembler) {
        this.matchRepository = matchRepository;
        this.querySeasons = querySeasons;
        this.assembler = assembler;
    }

    public Page execute(Criteria criteria) {
        LocalDate from = criteria.from();
        LocalDate to = criteria.to();
        if (criteria.seasonId() != null) {
            Season season = querySeasons.byId(criteria.seasonId()).season();
            from = from == null || from.isBefore(season.startDate()) ? season.startDate() : from;
            to = to == null || to.isAfter(season.endDate()) ? season.endDate() : to;
        }
        if (from != null && to != null && from.isAfter(to)) {
            return new Page(List.of(), criteria.page(), criteria.size(), false);
        }

        // Outcome depends on derived eligibility, so it is filtered after assembling. The group's
        // volume (hundreds of matches per year) keeps this cheap.
        List<MatchDetails> filtered = assembler.assembleAll(
                        matchRepository.findPlayed(startOf(from), endOf(to), criteria.playerId()))
                .stream()
                .filter(d -> criteria.outcomes().contains(MatchOutcome.of(d)))
                .toList();

        int start = Math.min(criteria.page() * criteria.size(), filtered.size());
        int end = Math.min(start + criteria.size(), filtered.size());
        return new Page(filtered.subList(start, end), criteria.page(), criteria.size(), end < filtered.size());
    }

    private static Instant startOf(LocalDate day) {
        return day == null ? null : day.atStartOfDay(BusinessCalendar.ZONE).toInstant();
    }

    private static Instant endOf(LocalDate day) {
        return day == null ? null : day.plusDays(1).atStartOfDay(BusinessCalendar.ZONE).toInstant();
    }
}
