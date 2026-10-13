package com.lucchetta.core.exception;

import java.util.List;
import java.util.UUID;

/**
 * A valid match with the same teams already exists on the same day (D-012). The user must
 * confirm that the new match is a different one.
 */
public class PossibleDuplicateMatchException extends ConflictException {

    private final List<UUID> existingMatchIds;

    public PossibleDuplicateMatchException(List<UUID> existingMatchIds) {
        super("POSSIBLE_DUPLICATE_MATCH");
        this.existingMatchIds = List.copyOf(existingMatchIds);
    }

    public List<UUID> existingMatchIds() {
        return existingMatchIds;
    }
}
