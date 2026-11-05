package com.lucchetta.core.domain.tournament;

import java.util.Comparator;

/**
 * A pair's record in its group. Order inside a group: wins, game difference, games won, then the
 * draw order (pair number).
 */
public record GroupStanding(int pair, int group, int played, int wins, int gamesWon, int gamesLost) {

    static final Comparator<GroupStanding> GROUP_ORDER = Comparator
            .comparingInt(GroupStanding::wins).reversed()
            .thenComparing(Comparator.comparingInt(GroupStanding::gameDifference).reversed())
            .thenComparing(Comparator.comparingInt(GroupStanding::gamesWon).reversed())
            .thenComparingInt(GroupStanding::pair);

    /**
     * Group winners of groups that may differ in size, compared per game played: win rate, game
     * difference per game, games won per game, then the group number.
     */
    static final Comparator<GroupStanding> ACROSS_GROUPS = ((Comparator<GroupStanding>) (a, b) ->
            Long.compare((long) b.wins * a.played, (long) a.wins * b.played))
            .thenComparing((a, b) -> Long.compare((long) b.gameDifference() * a.played,
                    (long) a.gameDifference() * b.played))
            .thenComparing((a, b) -> Long.compare((long) b.gamesWon * a.played, (long) a.gamesWon * b.played))
            .thenComparingInt(GroupStanding::group);

    public int gameDifference() {
        return gamesWon - gamesLost;
    }

    GroupStanding plus(int won, int lost) {
        return new GroupStanding(pair, group, played + 1, wins + (won > lost ? 1 : 0), gamesWon + won,
                gamesLost + lost);
    }
}
