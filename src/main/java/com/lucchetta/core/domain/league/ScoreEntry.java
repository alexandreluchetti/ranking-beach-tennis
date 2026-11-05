package com.lucchetta.core.domain.league;

/** Who records match scores in a league (decision D-029). */
public enum ScoreEntry {
    /** Any member (the default). */
    MEMBERS,
    /** League administrators (and the system administrator) only. */
    ADMINS
}
