package com.lucchetta.core.usecase.ranking;

import com.lucchetta.core.domain.BusinessCalendar;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.stats.RankingCalculator;
import com.lucchetta.core.stats.RankingEntry;
import com.lucchetta.core.stats.StatisticsCalculator;
import com.lucchetta.core.usecase.match.MatchDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds a season ranking from already assembled matches of the season's league.
 * <p>
 * Members: the league's members who joined up to the season's end date (so the active season
 * lists every member, starting at zero), plus anyone who played a match in it, even after
 * leaving the league (decision D-021).
 */
public final class SeasonRankingBuilder {

    private SeasonRankingBuilder() {
    }

    /**
     * @param members the league's current members
     * @param players at least every member and every registered participant of the matches
     */
    public static List<RankingEntry> build(Season season, Collection<MatchDetails> matches,
                                           Collection<LeagueMember> members, Collection<Player> players) {
        List<MatchDetails> seasonMatches = matches.stream()
                .filter(d -> d.season() != null && d.season().id().equals(season.id()))
                .toList();
        Set<UUID> ranked = seasonMatches.stream()
                .filter(d -> StatisticsCalculator.includedInStatistics(d.match()))
                .flatMap(d -> d.match().participants().stream())
                .map(Participant::playerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        members.stream()
                .filter(member -> !LocalDate.ofInstant(member.joinedAt(), BusinessCalendar.ZONE)
                        .isAfter(season.endDate()))
                .map(LeagueMember::playerId)
                .forEach(ranked::add);
        List<Player> rankedPlayers = players.stream().filter(p -> ranked.contains(p.id())).toList();
        return RankingCalculator.rank(rankedPlayers, StatisticsCalculator.byPlayer(seasonMatches));
    }

    /** Every member and every registered participant of the matches: the players a ranking may list. */
    static List<Player> playersOf(PlayerRepository playerRepository, Collection<LeagueMember> members,
                                  Collection<MatchDetails> matches) {
        Set<UUID> ids = members.stream().map(LeagueMember::playerId).collect(Collectors.toCollection(HashSet::new));
        matches.forEach(d -> ids.addAll(d.match().playerIds()));
        return playerRepository.findAllByIds(ids);
    }
}
