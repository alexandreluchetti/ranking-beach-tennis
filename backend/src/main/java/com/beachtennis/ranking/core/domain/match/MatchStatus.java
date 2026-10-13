package com.beachtennis.ranking.core.domain.match;

/**
 * Lifecycle of a match. Independent from ranking eligibility: a valid match may still not
 * count for the ranking (e.g. a guest took part).
 */
public enum MatchStatus {
    VALID,
    VOIDED
}
