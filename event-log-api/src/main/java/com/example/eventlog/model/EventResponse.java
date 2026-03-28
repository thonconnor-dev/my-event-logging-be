package com.example.eventlog.model;

import java.util.List;

public record EventResponse(
        boolean success,
        String status,
        String timestamp,
        String correlationId,
        String eventId,
        List<String> errors
) {
    public static EventResponse success(String timestamp, String correlationId, String eventId) {
        return new EventResponse(true, "logged", timestamp, correlationId, eventId, List.of());
    }

    public static EventResponse validationError(String timestamp, String correlationId, List<String> errors) {
        return new EventResponse(false, "validation_error", timestamp, correlationId, null, errors);
    }
}
