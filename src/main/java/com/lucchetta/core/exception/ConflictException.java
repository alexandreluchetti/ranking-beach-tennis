package com.lucchetta.core.exception;

/**
 * The operation conflicts with existing data.
 */
public class ConflictException extends BusinessException {

    public ConflictException(String code) {
        super(code);
    }
}
