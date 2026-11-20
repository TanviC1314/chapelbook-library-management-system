package com.chapelbook.common;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every failure into an RFC 9457 problem-details JSON body, so the frontend handles one error
 * shape. Unexpected errors are logged in full but only a generic message goes to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Rule violated", e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials(BadCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", e.getMessage());
    }

    /** A database constraint stopped the write: the last line of defence against races. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataIntegrity(DataIntegrityViolationException e) {
        log.warn("Database constraint violation: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The request conflicts with existing data. Please refresh and try again.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalidParameter(ConstraintViolationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Invalid request", "One or more parameters are invalid.");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Server error", "Something went wrong on our side.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Validation failed.");
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
