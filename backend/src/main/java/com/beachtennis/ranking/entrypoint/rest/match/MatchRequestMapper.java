package com.beachtennis.ranking.entrypoint.rest.match;

import com.beachtennis.ranking.core.domain.match.Participant;
import com.beachtennis.ranking.core.domain.match.Team;
import com.beachtennis.ranking.core.exception.RuleViolationException;

import java.util.ArrayList;
import java.util.List;

/** Shared request → domain mapping for registration and correction. */
final class MatchRequestMapper {

    private MatchRequestMapper() {
    }

    static List<Participant> participants(List<ParticipantRequest> teamA, List<ParticipantRequest> teamB) {
        List<Participant> participants = new ArrayList<>(4);
        addTeam(participants, Team.A, teamA);
        addTeam(participants, Team.B, teamB);
        return participants;
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
