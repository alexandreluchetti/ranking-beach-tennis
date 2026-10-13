package com.beachtennis.ranking.core.scoring;

import com.beachtennis.ranking.core.domain.match.Match;
import com.beachtennis.ranking.core.domain.match.Team;

/**
 * Decision D-003: games won by the player's team, plus 3 if the team won the match.
 * The tie-break counts as one game (D-002); tie-break points are ignored.
 */
public class GamesPlusWinBonusScoringPolicy implements ScoringPolicy {

    static final int WIN_BONUS = 3;

    @Override
    public int pointsPerPlayer(Match match, Team team) {
        int games = match.score().gamesOf(team);
        return match.winner() == team ? games + WIN_BONUS : games;
    }
}
