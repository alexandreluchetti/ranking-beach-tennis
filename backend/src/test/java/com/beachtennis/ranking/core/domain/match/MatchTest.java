package com.beachtennis.ranking.core.domain.match;

import com.beachtennis.ranking.core.exception.RuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BETO = UUID.randomUUID();
    private static final UUID CAIO = UUID.randomUUID();
    private static final UUID DANI = UUID.randomUUID();
    private static final Instant PLAYED = Instant.parse("2026-10-11T12:00:00Z");
    private static final Score SIX_FOUR = new Score(MatchFormat.STANDARD, 6, 4, null, null);

    @Test
    void requiresFourDistinctPositions() {
        assertThatThrownBy(() -> match(List.of(
                Participant.player(Team.A, 1, ANA), Participant.player(Team.A, 2, BETO),
                Participant.player(Team.B, 1, CAIO))))
                .hasMessage("INVALID_PARTICIPANTS");
        assertThatThrownBy(() -> match(List.of(
                Participant.player(Team.A, 1, ANA), Participant.player(Team.A, 1, BETO),
                Participant.player(Team.B, 1, CAIO), Participant.player(Team.B, 2, DANI))))
                .hasMessage("INVALID_PARTICIPANTS");
    }

    @Test
    void samePersonCannotTakeTwoPositions() {
        assertThatThrownBy(() -> match(List.of(
                Participant.player(Team.A, 1, ANA), Participant.player(Team.A, 2, BETO),
                Participant.player(Team.B, 1, ANA), Participant.player(Team.B, 2, DANI))))
                .isInstanceOf(RuleViolationException.class)
                .hasMessage("DUPLICATE_PARTICIPANT");
    }

    @Test
    void severalGuestsAreAllowed() {
        Match match = match(List.of(
                Participant.player(Team.A, 1, ANA), Participant.guest(Team.A, 2, null),
                Participant.guest(Team.B, 1, null), Participant.guest(Team.B, 2, "Zé")));

        assertThat(match.hasGuest()).isTrue();
    }

    @Test
    void sameTeamsIgnoreSideAndSlotOrder() {
        Match original = match(teams(ANA, BETO, CAIO, DANI));
        Match swapped = match(teams(DANI, CAIO, BETO, ANA));
        Match differentPairs = match(teams(ANA, CAIO, BETO, DANI));

        assertThat(original.hasSameTeamsAs(swapped)).isTrue();
        assertThat(original.hasSameTeamsAs(differentPairs)).isFalse();
    }

    @Test
    void sameTeamsComparesGuestsByName() {
        Match unnamedGuest = match(List.of(
                Participant.player(Team.A, 1, ANA), Participant.guest(Team.A, 2, null),
                Participant.player(Team.B, 1, CAIO), Participant.player(Team.B, 2, DANI)));
        Match unnamedGuestOtherSlot = match(List.of(
                Participant.player(Team.B, 2, ANA), Participant.guest(Team.B, 1, null),
                Participant.player(Team.A, 1, CAIO), Participant.player(Team.A, 2, DANI)));
        Match namedGuest = match(List.of(
                Participant.player(Team.A, 1, ANA), Participant.guest(Team.A, 2, "Zé"),
                Participant.player(Team.B, 1, CAIO), Participant.player(Team.B, 2, DANI)));

        assertThat(unnamedGuest.hasSameTeamsAs(unnamedGuestOtherSlot)).isTrue();
        assertThat(unnamedGuest.hasSameTeamsAs(namedGuest)).isFalse();
    }

    @Test
    void identicalGuestTeamsOnBothSidesDoNotBreakComparison() {
        List<Participant> allGuests = List.of(
                Participant.guest(Team.A, 1, null), Participant.guest(Team.A, 2, null),
                Participant.guest(Team.B, 1, null), Participant.guest(Team.B, 2, null));

        assertThat(match(allGuests).hasSameTeamsAs(match(allGuests))).isTrue();
    }

    @Test
    void contentComparisonIgnoresParticipantOrderAndSubSecondPrecision() {
        Match a = match(teams(ANA, BETO, CAIO, DANI));
        List<Participant> reordered = teams(ANA, BETO, CAIO, DANI).reversed();
        Match b = new Match(a.id(), PLAYED.plusNanos(123_456), SIX_FOUR, reordered, MatchStatus.VALID,
                UUID.randomUUID(), PLAYED, PLAYED);

        assertThat(a.hasSameContentAs(b)).isTrue();
        assertThat(a.hasSameContentAs(match(teams(ANA, BETO, CAIO, DANI), new Score(MatchFormat.STANDARD, 6, 3, null, null))))
                .isFalse();
    }

    @Test
    void playedOnUsesBrasiliaDate() {
        Match lateNight = new Match(UUID.randomUUID(), Instant.parse("2026-10-12T01:30:00Z"), SIX_FOUR,
                teams(ANA, BETO, CAIO, DANI), MatchStatus.VALID, ANA, PLAYED, PLAYED);

        assertThat(lateNight.playedOn()).isEqualTo(LocalDate.of(2026, 10, 11));
    }

    @Test
    void requiresDate() {
        assertThatThrownBy(() -> new Match(UUID.randomUUID(), null, SIX_FOUR, teams(ANA, BETO, CAIO, DANI),
                MatchStatus.VALID, ANA, PLAYED, PLAYED))
                .hasMessage("INVALID_MATCH_DATE");
    }

    @Test
    void guestNameIsNormalizedAndLimited() {
        assertThat(Participant.guest(Team.A, 1, "  Zé   da Silva ").guestName()).isEqualTo("Zé da Silva");
        assertThat(Participant.guest(Team.A, 1, "   ").guestName()).isNull();
        assertThatThrownBy(() -> Participant.guest(Team.A, 1, "x".repeat(41))).hasMessage("INVALID_GUEST_NAME");
    }

    private static List<Participant> teams(UUID a1, UUID a2, UUID b1, UUID b2) {
        return List.of(Participant.player(Team.A, 1, a1), Participant.player(Team.A, 2, a2),
                Participant.player(Team.B, 1, b1), Participant.player(Team.B, 2, b2));
    }

    private static Match match(List<Participant> participants) {
        return match(participants, SIX_FOUR);
    }

    private static Match match(List<Participant> participants, Score score) {
        return new Match(UUID.randomUUID(), PLAYED, score, participants, MatchStatus.VALID, ANA, PLAYED, PLAYED);
    }
}
