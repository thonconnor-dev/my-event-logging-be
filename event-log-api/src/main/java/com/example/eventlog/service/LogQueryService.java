package com.example.eventlog.service;

import com.example.eventlog.model.CacheState;
import com.example.eventlog.model.CacheStatusResponse;
import com.example.eventlog.model.LogPageResponse;
import com.example.eventlog.model.LogRecord;
import com.example.eventlog.model.LogRecordResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class LogQueryService {

    private static final int DEFAULT_LIMIT = 50;

    private final TransientLogCache cache;
    private final LogCursorCodec cursorCodec;
    private final Clock clock;

    public LogQueryService(TransientLogCache cache, LogCursorCodec cursorCodec, Clock clock) {
        this.cache = cache;
        this.cursorCodec = cursorCodec;
        this.clock = clock;
    }

    public LogPageResponse fetchLogs(Integer limit, String cursor) {
        int resolvedLimit = resolveLimit(limit);
        List<LogRecord> ordered = cache.snapshot()
                .stream()
                .sorted(Comparator.comparing(LogRecord::timestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed()
                        .thenComparing(LogRecord::id))
                .toList();

        int startIndex = cursorCodec.decode(cursor)
                .map(id -> indexOf(ordered, id) + 1)
                .filter(idx -> idx > 0 && idx <= ordered.size())
                .orElse(0);

        int endIndex = Math.min(ordered.size(), startIndex + resolvedLimit);
        List<LogRecord> slice = ordered.subList(startIndex, endIndex);

        String nextCursor = null;
        if (endIndex < ordered.size() && !slice.isEmpty()) {
            nextCursor = cursorCodec.encode(slice.get(slice.size() - 1).id());
        }

        slice.forEach(cache::ensurePresent);

        List<LogRecordResponse> responses = slice.stream()
                .map(this::toResponse)
                .toList();

        CacheStatusResponse cacheStatus = buildStatus();
        boolean dataComplete = cacheStatus.state() != CacheState.TRUNCATED;

        return new LogPageResponse(responses, nextCursor, dataComplete, cacheStatus);
    }

    private int resolveLimit(Integer limit) {
        int defaultLimit = Math.min(DEFAULT_LIMIT, cache.maxPageSize());
        if (limit == null) {
            return defaultLimit;
        }
        if (limit < 1) {
            return defaultLimit;
        }
        return Math.min(limit, cache.maxPageSize());
    }

    private int indexOf(List<LogRecord> ordered, String id) {
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private LogRecordResponse toResponse(LogRecord record) {
        return new LogRecordResponse(
                record.id(),
                record.callerId(),
                record.message(),
                record.metadata(),
                record.timestamp(),
                record.severity(),
                record.source(),
                record.correlationId()
        );
    }

    private CacheStatusResponse buildStatus() {
        CacheState state = cache.currentState();
        Instant lastRefresh = cache.lastRefresh();
        long stalenessSeconds = Duration.between(lastRefresh, Instant.now(clock)).getSeconds();
        if (stalenessSeconds < 0) {
            stalenessSeconds = 0;
        }
        return new CacheStatusResponse(
                state,
                lastRefresh,
                cache.evictionCount(),
                stalenessSeconds
        );
    }
}
