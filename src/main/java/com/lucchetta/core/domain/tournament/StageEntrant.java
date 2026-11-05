package com.lucchetta.core.domain.tournament;

import com.lucchetta.core.exception.RuleViolationException;

import java.util.UUID;

/**
 * Someone present at a daily tournament stage (D-027): a league member, or a guest by name
 * (D-004).
 *
 * @param position order in which the administrator listed the players, from 1
 */
public record StageEntrant(int position, UUID playerId, String guestName) {

    public static final int GUEST_NAME_MAX_LENGTH = 40;

    public StageEntrant {
        if (position < 1) {
            throw new IllegalArgumentException("position must be positive");
        }
        if (playerId != null) {
            guestName = null;
        } else {
            guestName = guestName == null ? "" : guestName.strip().replaceAll("\s+", " ");
            if (guestName.isEmpty() || guestName.length() > GUEST_NAME_MAX_LENGTH) {
                throw new RuleViolationException("INVALID_STAGE_GUEST_NAME");
            }
        }
    }

    public boolean guest() {
        return playerId == null;
    }
}
