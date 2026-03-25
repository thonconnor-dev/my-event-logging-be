package com.example.eventlog.model;

import java.time.Instant;
import java.util.Map;

public record ResolvedEvent(
        String callerId,
        String message,
        Map<String, String> metadata,
        Instant resolvedTimestamp,
        TimestampSource source,
        String correlationId
) {
    public enum TimestampSource {
        SERVER,
        CLIENT
    }
}
