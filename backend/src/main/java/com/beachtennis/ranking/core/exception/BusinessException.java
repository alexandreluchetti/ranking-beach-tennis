package com.beachtennis.ranking.core.exception;

/**
 * Violation of a business rule. The {@code code} is stable and is translated into a user-facing
 * message by the entrypoint layer.
 */
public abstract class BusinessException extends RuntimeException {

    private final String code;

    protected BusinessException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
