package com.example.eventlog.support;

import com.example.eventlog.model.EventRecordEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared factory helpers for creating persisted {@link EventRecordEntity} instances in tests.
 * Centralizing this logic keeps pagination/integration tests consistent.
 */
public final class EventRecordFixtures {

    private static final Instant DEFAULT_RESOLVED = Instant.parse("2026-03-01T00:00:00Z");

    private EventRecordFixtures() {
    }

    public static EventRecordEntity sampleRecord() {
        return builder().build();
    }

    public static EventRecordEntity sampleRecord(String callerId, Instant resolved) {
        return builder()
                .callerId(callerId)
                .resolvedTimestamp(resolved)
                .receivedTimestamp(resolved)
                .clientTimestamp(resolved)
                .build();
    }

    public static EventRecordEntity.Builder builder() {
        Instant resolved = DEFAULT_RESOLVED;
        return EventRecordEntity.builder()
                .id(UUID.randomUUID())
                .callerId("fixture-client")
                .severity("INFO")
                .message("fixture message")
                .metadataJson("{}")
                .clientTimestamp(resolved)
                .receivedTimestamp(resolved)
                .resolvedTimestamp(resolved)
                .timestampSource("SERVER")
                .messageHash("hash-" + UUID.randomUUID())
                .status("LOGGED")
                .correlationId(UUID.randomUUID().toString())
                .expiresAt(resolved.plusSeconds(60));
    }
}
