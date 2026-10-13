package com.lucchetta.core.usecase.ranking;

import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.stats.RankingCalculator;
import com.lucchetta.core.stats.RankingEntry;
import com.lucchetta.core.stats.StatisticsCalculator;
import com.lucchetta.core.usecase.match.MatchDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds a season ranking from already assembled matches.
 * <p>
 * Members: every player registered up to the season's end date (so the active season lists
 * everyone, starting at zero, D-001), plus anyone who played a match in it.
 */
public final class SeasonRankingBuilder {

    private SeasonRankingBuilder() {
    }

    public static List<RankingEntry> build(Season season, Collection<MatchDetails> matches, Collection<Player> players) {
        List<MatchDetails> seasonMatches = matches.stream()
                .filter(d -> d.season() != null && d.season().id().equals(season.id()))
                .toList();
        Set<UUID> participants = seasonMatches.stream()
                .filter(d -> StatisticsCalculator.includedInStatistics(d.match()))
                .flatMap(d -> d.match().participants().stream())
                .map(Participant::playerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<Player> members = players.stream()
                .filter(p -> !registeredOn(p).isAfter(season.endDate()) || participants.contains(p.id()))
                .toList();
        return RankingCalculator.rank(members, StatisticsCalculator.byPlayer(seasonMatches));
    }

    private static LocalDate registeredOn(Player player) {
        return LocalDate.ofInstant(player.createdAt(), BusinessCalendar.ZONE);
    }
}
