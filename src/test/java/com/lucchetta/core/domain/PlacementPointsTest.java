package com.lucchetta.core.domain;

import com.lucchetta.core.exception.RuleViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlacementPointsTest {

    @ParameterizedTest(name = "{0}/{1}/{2}/{3}/{4}")
    @CsvSource({
            "100,70,50,35,20",
            "10,10,10,10,10",
            "1000,0,0,0,0",
            "0,0,0,0,0"})
    void acceptsTablesWhereABetterPlacementNeverGivesLess(int champion, int runnerUp, int semifinal,
                                                         int quarterfinal, int groupStage) {
        assertThat(new PlacementPoints(champion, runnerUp, semifinal, quarterfinal, groupStage).champion())
                .isEqualTo(champion);
    }

    @ParameterizedTest(name = "{0}/{1}/{2}/{3}/{4}")
    @CsvSource({
            "70,100,50,35,20",
            "100,70,50,35,40",
            "100,70,50,35,-1",
            "1001,70,50,35,20"})
    void rejectsOutOfOrderOrOutOfRangeTables(int champion, int runnerUp, int semifinal, int quarterfinal,
                                             int groupStage) {
        assertThatThrownBy(() -> new PlacementPoints(champion, runnerUp, semifinal, quarterfinal, groupStage))
                .isInstanceOf(RuleViolationException.class)
                .hasMessage("INVALID_PLACEMENT_POINTS");
    }
}
