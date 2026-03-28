package com.example.eventlog.model;

import java.util.List;

public record LogPageResponse(List<LogRecordResponse> events, String nextPageToken,
                boolean dataComplete, CacheStatusResponse cacheStatus) {
}
