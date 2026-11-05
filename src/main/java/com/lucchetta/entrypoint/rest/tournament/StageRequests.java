package com.lucchetta.entrypoint.rest.tournament;

import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.usecase.tournament.ManageStages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request bodies of the stage endpoints. */
public final class StageRequests {

    private StageRequests() {
    }

    /** A member ({@code playerId}) or a guest ({@code guestName}). */
    public record AttendeeRequest(UUID playerId, String guestName) {

        ManageStages.Attendee toAttendee() {
            return new ManageStages.Attendee(playerId, guestName);
        }
    }

    /**
     * @param date    the playing day; omitted means today
     * @param players present players, 8 to 32, even (D-027)
     */
    public record CreateStageRequest(LocalDate date, @NotNull MatchFormat format,
                                     @NotNull List<@NotNull @Valid AttendeeRequest> players) {
    }

    /** Omitted fields keep the current value. */
    public record RedrawRequest(MatchFormat format, List<@NotNull @Valid AttendeeRequest> players) {
    }

    /**
     * @param expectedMatchVersion when correcting, the version of the match the administrator saw
     * @param reason               optional justification of a correction
     */
    public record ScoreRequest(@NotNull Integer teamAGames, @NotNull Integer teamBGames, Integer tiebreakTeamAPoints,
                               Integer tiebreakTeamBPoints, Integer expectedMatchVersion, String reason) {
    }

    public record ClearScoreRequest(String reason) {
    }

    static List<ManageStages.Attendee> attendees(List<AttendeeRequest> players) {
        return players == null ? null : players.stream().map(AttendeeRequest::toAttendee).toList();
    }
}
