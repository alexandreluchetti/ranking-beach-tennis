package com.lucchetta.core.stats;

/**
 * A player's stages in a daily tournament season (D-028): finished stages played, titles
 * (champion) and finals (champion or runner-up).
 */
public record TournamentRecord(int stages, int titles, int finals) {

    public static final TournamentRecord EMPTY = new TournamentRecord(0, 0, 0);

    TournamentRecord plus(boolean title, boolean fin) {
        return new TournamentRecord(stages + 1, titles + (title ? 1 : 0), finals + (fin ? 1 : 0));
    }

    static TournamentRecord sum(TournamentRecord a, TournamentRecord b) {
        return new TournamentRecord(a.stages + b.stages, a.titles + b.titles, a.finals + b.finals);
    }
}
