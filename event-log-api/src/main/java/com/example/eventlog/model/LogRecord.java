package com.example.eventlog.model;

import com.example.eventlog.model.ResolvedEvent.TimestampSource;

import java.time.Instant;
import java.util.Map;

public record LogRecord(
        String id,
        String callerId,
        String message,
        Map<String, String> metadata,
        Instant timestamp,
        LogSeverity severity,
        TimestampSource source,
        String correlationId
) {
}
