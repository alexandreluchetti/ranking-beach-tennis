package com.lucchetta.core.stats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerStatsTest {

    @Test
    void winRateIsRoundedToAnInteger() {
        assertThat(stats(11, 7).winRate()).isEqualTo(64);
        assertThat(stats(3, 1).winRate()).isEqualTo(33);
        assertThat(stats(3, 2).winRate()).isEqualTo(67);
        assertThat(stats(8, 1).winRate()).isEqualTo(13); // 12.5 rounds up
        assertThat(stats(4, 4).winRate()).isEqualTo(100);
        assertThat(stats(4, 0).winRate()).isZero();
    }

    @Test
    void noWinRateWithoutMatches() {
        assertThat(PlayerStats.EMPTY.winRate()).isNull();
    }

    private static PlayerStats stats(int matches, int wins) {
        return new PlayerStats(matches, wins, matches - wins, 0, 0, 0);
    }
}
