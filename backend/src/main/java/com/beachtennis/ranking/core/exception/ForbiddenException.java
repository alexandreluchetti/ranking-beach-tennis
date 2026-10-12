package com.beachtennis.ranking.core.exception;

/**
 * The caller is not allowed to perform the operation.
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super("FORBIDDEN");
    }
}
