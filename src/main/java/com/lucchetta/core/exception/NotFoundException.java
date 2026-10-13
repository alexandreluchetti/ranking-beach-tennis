package com.lucchetta.core.exception;

public class NotFoundException extends BusinessException {

    public NotFoundException(String code) {
        super(code);
    }
}
