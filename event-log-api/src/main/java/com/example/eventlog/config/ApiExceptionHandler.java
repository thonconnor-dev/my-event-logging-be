package com.example.eventlog.config;

import com.example.eventlog.model.EventResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<EventResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
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

    private ResponseEntity<EventResponse> buildBadRequest(List<String> errors) {
        String correlationId = MDC.get("correlationId");
        EventResponse response = EventResponse.validationError(Instant.now().toString(), correlationId, errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
