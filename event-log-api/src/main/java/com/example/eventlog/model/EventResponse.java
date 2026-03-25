package com.example.eventlog.model;

import java.util.List;

public record EventResponse(
        boolean success,
        String status,
        String timestamp,
        String correlationId,
        List<String> errors
) {
    public static EventResponse success(String timestamp, String correlationId) {
        return new EventResponse(true, "logged", timestamp, correlationId, List.of());
    }

    public static EventResponse validationError(String timestamp, String correlationId, List<String> errors) {
        return new EventResponse(false, "validation_error", timestamp, correlationId, errors);
    }
}
