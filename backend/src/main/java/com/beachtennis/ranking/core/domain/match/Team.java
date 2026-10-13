package com.beachtennis.ranking.core.domain.match;

public enum Team {
    A,
    B;

    public Team opponent() {
        return this == A ? B : A;
    }
}
