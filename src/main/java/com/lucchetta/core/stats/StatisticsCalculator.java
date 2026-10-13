package com.lucchetta.core.stats;

import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.usecase.match.MatchDetails;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Statistics inclusion rule, kept apart from scoring: every valid match counts for statistics,
 * including guest matches and matches outside any season (D-004); voided matches never count.
 * Points come from the already evaluated {@link MatchDetails}, so they are only non-zero for
 * matches that count for the ranking.
 */
public final class StatisticsCalculator {

    private StatisticsCalculator() {
    }

    public static boolean includedInStatistics(Match match) {
        return match.status() == MatchStatus.VALID;
    }

    /** Statistics for every registered player that took part in the given matches. */
    public static Map<UUID, PlayerStats> byPlayer(Collection<MatchDetails> matches) {
        Map<UUID, PlayerStats> stats = new HashMap<>();
        for (MatchDetails details : matches) {
            Match match = details.match();
            if (!includedInStatistics(match)) {
                continue;
            }
            for (Participant p : match.participants()) {
                if (p.isGuest()) {
                    continue;
                }
                int own = match.score().gamesOf(p.team());
                int opponent = match.score().gamesOf(p.team().opponent());
                stats.merge(p.playerId(),
                        PlayerStats.EMPTY.plus(match.winner() == p.team(), own, opponent, details.pointsFor(p.team())),
                        StatisticsCalculator::sum);
            }
        }
        return stats;
    }

    private static PlayerStats sum(PlayerStats a, PlayerStats b) {
        return new PlayerStats(a.matchesPlayed() + b.matchesPlayed(), a.wins() + b.wins(), a.losses() + b.losses(),
                a.gamesWon() + b.gamesWon(), a.gamesLost() + b.gamesLost(), a.points() + b.points());
    }
}
