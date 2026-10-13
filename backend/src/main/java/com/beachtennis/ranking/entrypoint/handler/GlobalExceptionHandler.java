package com.beachtennis.ranking.entrypoint.handler;

import com.beachtennis.ranking.core.exception.BusinessException;
import com.beachtennis.ranking.core.exception.ConflictException;
import com.beachtennis.ranking.core.exception.ForbiddenException;
import com.beachtennis.ranking.core.exception.NotFoundException;
import com.beachtennis.ranking.core.exception.PossibleDuplicateMatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Converts errors into RFC 9457 problem details carrying a stable {@code code} and a pt-BR
 * message ready to be shown by the app.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException exception) {
        ProblemDetail problem = problem(statusOf(exception), exception.code());
        if (exception instanceof PossibleDuplicateMatchException duplicate) {
            problem.setProperty("existingMatchIds", duplicate.existingMatchIds());
        }
        return problem;
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ProblemDetail handleInvalidRequest(Exception exception) {
        log.info("Rejected malformed request: {}", exception.getMessage());
        return problem(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_REQUEST);
    }

    private static HttpStatus statusOf(BusinessException exception) {
        return switch (exception) {
            case NotFoundException e -> HttpStatus.NOT_FOUND;
            case ForbiddenException e -> HttpStatus.FORBIDDEN;
            case ConflictException e -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private static ProblemDetail problem(HttpStatus status, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ErrorMessages.forCode(code));
        problem.setProperty("code", code);
        return problem;
    }
}
