package com.example.eventlog.model;

import java.util.List;

public record LogPageResponse(
        List<LogRecordResponse> items,
        String nextCursor,
        boolean dataComplete,
        CacheStatusResponse cacheStatus
) {
}
