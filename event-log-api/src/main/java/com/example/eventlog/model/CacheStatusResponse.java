package com.example.eventlog.model;

import java.time.Instant;

public record CacheStatusResponse(
        CacheState state,
        Instant lastRefresh,
        long evictionCount,
        long stalenessSeconds
) {
}
