package com.beachtennis.ranking.entrypoint.handler;

import com.beachtennis.ranking.core.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts errors into RFC 9457 problem details carrying a stable {@code code} and a pt-BR
 * message ready to be shown by the app.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.code());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail handleInvalidRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_REQUEST);
    }

    private static ProblemDetail problem(HttpStatus status, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ErrorMessages.forCode(code));
        problem.setProperty("code", code);
        return problem;
    }
}
