package com.example.eventlog.config;

import com.example.eventlog.model.EventResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<EventResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getDefaultMessage())
                .collect(Collectors.toList());
        return buildBadRequest(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<EventResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations()
                .stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.toList());
        return buildBadRequest(errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<EventResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return buildBadRequest(List.of(ex.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<EventResponse> handleDataAccess(DataAccessException ex) {
        return buildServerError(List.of("Persistence temporarily unavailable"));
    }

    private ResponseEntity<EventResponse> buildBadRequest(List<String> errors) {
        String correlationId = MDC.get("correlationId");
        EventResponse response = EventResponse.validationError(Instant.now().toString(), correlationId, errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    private ResponseEntity<EventResponse> buildServerError(List<String> errors) {
        String correlationId = MDC.get("correlationId");
        EventResponse response = new EventResponse(false, "persistence_error", Instant.now().toString(), correlationId, null, errors);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
