package com.beachtennis.ranking.core.exception;

/**
 * The request is well-formed but breaks a business rule.
 */
public class RuleViolationException extends BusinessException {

    public RuleViolationException(String code) {
        super(code);
    }
}
