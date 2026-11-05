package com.lucchetta.core.usecase.tournament;

import com.lucchetta.core.domain.tournament.FixtureResult;
import com.lucchetta.core.domain.tournament.GroupStanding;
import com.lucchetta.core.domain.tournament.Placement;
import com.lucchetta.core.domain.tournament.TournamentStage;
import com.lucchetta.core.usecase.match.MatchDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A stage with everything needed to present it.
 *
 * @param matches        match of each scored game, by fixture number
 * @param results        games of each scored game, by fixture number
 * @param standings      group standings, best first, by group number
 * @param placements     placement of each pair by pair number; empty until the final has a score
 * @param playerNames    display names of the members present
 * @param canEnterScores whether the caller may enter the score of a game without one (D-029)
 * @param canAdminister  whether the caller may draw again, correct and clear scores, reopen the
 *                       groups or delete the stage
 */
public record StageView(
        TournamentStage stage,
        Map<Integer, MatchDetails> matches,
        Map<Integer, FixtureResult> results,
        Map<Integer, List<GroupStanding>> standings,
        Map<Integer, Placement> placements,
        Map<UUID, String> playerNames,
        boolean canEnterScores,
        boolean canAdminister) {
}
