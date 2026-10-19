package com.lucchetta.core.scoring;

/**
 * Why a match gives no ranking points. A voided match is a different thing from a valid match
 * that does not score, but both are reported here so the history can explain it.
 */
public enum NotCountedReason {
    /** The match was voided by the administrator. */
    VOIDED,
    /** The match date is not within any season. */
    NO_SEASON
}
