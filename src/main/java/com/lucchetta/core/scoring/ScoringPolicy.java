package com.lucchetta.core.scoring;

import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.Team;

/**
 * How many ranking points each member of a team earns from a match that counts for the ranking.
 * The single place where the scoring formula lives; whether a match counts is decided
 * separately by {@link RankingEligibilityPolicy}.
 */
public interface ScoringPolicy {

    int pointsPerPlayer(Match match, Team team);
}
