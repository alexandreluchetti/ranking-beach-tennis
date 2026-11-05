package com.lucchetta.core.exception;

/**
 * The caller is not allowed to perform the operation.
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super("FORBIDDEN");
    }

    /** A more specific reason, for the app to explain it. */
    public ForbiddenException(String code) {
        super(code);
    }
}
