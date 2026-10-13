package com.beachtennis.ranking.entrypoint.rest.match;

import com.beachtennis.ranking.core.domain.match.Participant;
import com.beachtennis.ranking.core.domain.match.Score;
import com.beachtennis.ranking.core.domain.match.Team;
import com.beachtennis.ranking.core.exception.RuleViolationException;
import com.beachtennis.ranking.core.usecase.match.QueryMatch;
import com.beachtennis.ranking.core.usecase.match.RegisterMatch;
import com.beachtennis.ranking.entrypoint.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final CurrentUserProvider currentUserProvider;
    private final RegisterMatch registerMatch;
    private final QueryMatch queryMatch;

    public MatchController(CurrentUserProvider currentUserProvider, RegisterMatch registerMatch,
                           QueryMatch queryMatch) {
        this.currentUserProvider = currentUserProvider;
        this.registerMatch = registerMatch;
        this.queryMatch = queryMatch;
    }

    @PostMapping
    @Operation(summary = "Registers a finished match",
            description = "Idempotent by id: 201 when created, 200 when the same match was already registered. "
                    + "409 POSSIBLE_DUPLICATE_MATCH asks the user to confirm (resend with confirmPossibleDuplicate).")
    public ResponseEntity<MatchResponse> register(@Valid @RequestBody RegisterMatchRequest request) {
        RegisterMatch.Result result = registerMatch.execute(currentUserProvider.get(), toCommand(request));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(MatchResponse.from(result.details()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Returns one match")
    public MatchResponse get(@PathVariable UUID id) {
        return MatchResponse.from(queryMatch.byId(id));
    }

    private static RegisterMatch.Command toCommand(RegisterMatchRequest request) {
        Score score = new Score(request.format(), request.teamAGames(), request.teamBGames(),
                request.tiebreakTeamAPoints(), request.tiebreakTeamBPoints());
        List<Participant> participants = new ArrayList<>(4);
        addTeam(participants, Team.A, request.teamA());
        addTeam(participants, Team.B, request.teamB());
        return new RegisterMatch.Command(request.id(), request.playedAt().toInstant(), score, participants,
                request.duplicateConfirmed());
    }

    private static void addTeam(List<Participant> participants, Team team, List<ParticipantRequest> members) {
        for (int i = 0; i < members.size(); i++) {
            ParticipantRequest member = members.get(i);
            int slot = i + 1;
            if (member.isGuest() == (member.playerId() != null)) {
                throw new RuleViolationException("INVALID_PARTICIPANTS");
            }
            participants.add(member.isGuest()
                    ? Participant.guest(team, slot, member.guestName())
                    : Participant.player(team, slot, member.playerId()));
        }
    }
}
