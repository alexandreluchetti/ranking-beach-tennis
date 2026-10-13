package com.beachtennis.ranking.core.scoring;

import com.beachtennis.ranking.core.domain.match.Match;
import com.beachtennis.ranking.core.domain.match.Team;

/**
 * How many ranking points each member of a team earns from a match that counts for the ranking.
 * The single place where the scoring formula lives; whether a match counts is decided
 * separately by {@link RankingEligibilityPolicy}.
 */
public interface ScoringPolicy {

    int pointsPerPlayer(Match match, Team team);
}
