package com.beachtennis.ranking.entrypoint.rest.match;

import com.beachtennis.ranking.core.domain.BusinessCalendar;
import com.beachtennis.ranking.core.domain.match.Match;
import com.beachtennis.ranking.core.domain.match.MatchFormat;
import com.beachtennis.ranking.core.domain.match.MatchStatus;
import com.beachtennis.ranking.core.domain.match.Team;
import com.beachtennis.ranking.core.scoring.NotCountedReason;
import com.beachtennis.ranking.core.usecase.match.MatchDetails;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A match as shown in the app. {@code status} (valid/voided) and {@code counted} (ranking
 * eligibility) are independent; {@code notCountedReason} explains a match that gives no points.
 */
public record MatchResponse(
        UUID id,
        OffsetDateTime playedAt,
        MatchFormat format,
        int teamAGames,
        int teamBGames,
        Integer tiebreakTeamAPoints,
        Integer tiebreakTeamBPoints,
        Team winner,
        MatchStatus status,
        boolean counted,
        NotCountedReason notCountedReason,
        SeasonSummary season,
        List<ParticipantResponse> teamA,
        List<ParticipantResponse> teamB,
        UUID registeredBy,
        OffsetDateTime registeredAt,
        VoidSummary voiding,
        int version) {

    public record SeasonSummary(UUID id, String name) {
    }

    /** Present only while the match is voided. */
    public record VoidSummary(String reason, UUID voidedBy, OffsetDateTime voidedAt) {
    }

    /**
     * @param displayName registered player's current name; null for a guest
     * @param guestName   optional guest name; null for a registered player or an unnamed guest
     * @param points      ranking points this participant received from the match
     */
    public record ParticipantResponse(int slot, UUID playerId, String displayName, boolean guest, String guestName,
                                      int points) {
    }

    static MatchResponse from(MatchDetails details) {
        Match match = details.match();
        return new MatchResponse(
                match.id(),
                OffsetDateTime.ofInstant(match.playedAt(), BusinessCalendar.ZONE),
                match.format(),
                match.score().teamAGames(),
                match.score().teamBGames(),
                match.score().tiebreakTeamAPoints(),
                match.score().tiebreakTeamBPoints(),
                match.winner(),
                match.status(),
                details.eligibility().counted(),
                details.eligibility().reason(),
                details.season() == null ? null : new SeasonSummary(details.season().id(), details.season().name()),
                team(details, Team.A),
                team(details, Team.B),
                match.registeredBy(),
                OffsetDateTime.ofInstant(match.registeredAt(), BusinessCalendar.ZONE),
                match.voiding() == null ? null : new VoidSummary(match.voiding().reason(), match.voiding().voidedBy(),
                        OffsetDateTime.ofInstant(match.voiding().voidedAt(), BusinessCalendar.ZONE)),
                match.version());
    }

    private static List<ParticipantResponse> team(MatchDetails details, Team team) {
        int points = details.pointsFor(team);
        return details.match().team(team).stream()
                .map(p -> new ParticipantResponse(p.slot(), p.playerId(),
                        p.isGuest() ? null : details.playerNames().get(p.playerId()),
                        p.isGuest(), p.guestName(), p.isGuest() ? 0 : points))
                .toList();
    }
}
