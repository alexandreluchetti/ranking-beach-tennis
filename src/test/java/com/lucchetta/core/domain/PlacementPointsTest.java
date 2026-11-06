package com.lucchetta.core.domain;

import com.lucchetta.core.domain.tournament.Placement;
import com.lucchetta.core.exception.RuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlacementPointsTest {

    @ParameterizedTest(name = "{0}/{1}/{2}/{3}/{4}/{5}/{6}")
    @CsvSource({
            "100,70,55,45,35,25,20",
            "10,10,10,10,10,10,10",
            "1000,0,0,0,0,0,0",
            "0,0,0,0,0,0,0"})
    void acceptsTablesWhereABetterPlacementNeverGivesLess(int champion, int runnerUp, int third, int fourth,
                                                         int quarterfinal, int roundOf16, int groupStage) {
        assertThat(new PlacementPoints(champion, runnerUp, third, fourth, quarterfinal, roundOf16, groupStage)
                .champion()).isEqualTo(champion);
    }

    @ParameterizedTest(name = "{0}/{1}/{2}/{3}/{4}/{5}/{6}")
    @CsvSource({
            "70,100,55,45,35,25,20",
            "100,70,45,55,35,25,20",
            "100,70,55,45,25,35,20",
            "100,70,55,45,35,25,40",
            "100,70,55,45,35,25,-1",
            "1001,70,55,45,35,25,20"})
    void rejectsOutOfOrderOrOutOfRangeTables(int champion, int runnerUp, int third, int fourth, int quarterfinal,
                                             int roundOf16, int groupStage) {
        assertThatThrownBy(() -> new PlacementPoints(champion, runnerUp, third, fourth, quarterfinal, roundOf16,
                groupStage))
                .isInstanceOf(RuleViolationException.class)
                .hasMessage("INVALID_PLACEMENT_POINTS");
    }

    @Test
    void eachPlacementHasItsPointsAndAnOldSemifinalCountsAsFourth() {
        PlacementPoints table = PlacementPoints.DEFAULT;

        assertThat(table.pointsFor(Placement.CHAMPION)).isEqualTo(100);
        assertThat(table.pointsFor(Placement.RUNNER_UP)).isEqualTo(70);
        assertThat(table.pointsFor(Placement.THIRD)).isEqualTo(55);
        assertThat(table.pointsFor(Placement.FOURTH)).isEqualTo(45);
        assertThat(table.pointsFor(Placement.SEMIFINAL)).isEqualTo(45);
        assertThat(table.pointsFor(Placement.QUARTERFINAL)).isEqualTo(35);
        assertThat(table.pointsFor(Placement.ROUND_OF_16)).isEqualTo(25);
        assertThat(table.pointsFor(Placement.GROUP_STAGE)).isEqualTo(20);
    }
}
