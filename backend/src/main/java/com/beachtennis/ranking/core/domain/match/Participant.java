package com.beachtennis.ranking.core.domain.match;

import com.beachtennis.ranking.core.exception.RuleViolationException;

import java.util.Objects;
import java.util.UUID;

/**
 * One of the four positions of a match: a registered player or a guest. Guest status is
 * captured at registration and never changes, even if the person signs up later (D-004).
 *
 * @param slot      1 or 2 within the team
 * @param playerId  registered player, null for a guest
 * @param guestName optional guest name, always null for a registered player
 */
public record Participant(Team team, int slot, UUID playerId, String guestName) {

    public static final int GUEST_NAME_MAX_LENGTH = 40;

    public Participant {
        Objects.requireNonNull(team, "team");
        if (slot != 1 && slot != 2) {
            throw new RuleViolationException("INVALID_PARTICIPANTS");
        }
        if (playerId != null) {
            guestName = null;
        } else if (guestName != null) {
            guestName = guestName.strip().replaceAll("\\s+", " ");
            if (guestName.isEmpty()) {
                guestName = null;
            } else if (guestName.length() > GUEST_NAME_MAX_LENGTH) {
                throw new RuleViolationException("INVALID_GUEST_NAME");
            }
        }
    }

    public static Participant player(Team team, int slot, UUID playerId) {
        return new Participant(team, slot, Objects.requireNonNull(playerId, "playerId"), null);
    }

    public static Participant guest(Team team, int slot, String guestName) {
        return new Participant(team, slot, null, guestName);
    }

    public boolean isGuest() {
        return playerId == null;
    }
}
