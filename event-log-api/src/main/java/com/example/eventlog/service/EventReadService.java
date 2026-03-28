package com.example.eventlog.service;

import com.example.eventlog.model.CacheState;
import com.example.eventlog.model.CacheStatusResponse;
import com.example.eventlog.model.EventRecordEntity;
import com.example.eventlog.model.LogPageResponse;
import com.example.eventlog.model.LogRecord;
import com.example.eventlog.model.LogRecordResponse;
import com.example.eventlog.model.LogSeverity;
import com.example.eventlog.model.ResolvedEvent;
import com.example.eventlog.repository.EventRecordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventReadService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;
    private static final Duration DEFAULT_RANGE = Duration.ofDays(30);

    private final EventRecordRepository repository;
    private final TransientLogCache cache;
    private final LogCursorCodec cursorCodec;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EventReadService(EventRecordRepository repository,
                            TransientLogCache cache,
                            LogCursorCodec cursorCodec,
                            ObjectMapper objectMapper,
                            Clock clock) {
        this.repository = repository;
        this.cache = cache;
        this.cursorCodec = cursorCodec;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public LogPageResponse fetchLogs(Instant from, Instant to, Integer pageSize, String pageToken) {
        Instant now = Instant.now(clock);
        Instant effectiveTo = to == null ? now : to;
        Instant effectiveFrom = from == null ? effectiveTo.minus(DEFAULT_RANGE) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }

        int limit = resolvePageSize(pageSize);
        Optional<LogCursorCodec.PageCursor> cursor = cursorCodec.decode(pageToken);
        Instant lastTimestamp = cursor.map(LogCursorCodec.PageCursor::lastResolvedTimestamp).orElse(null);
        UUID lastId = cursor.map(LogCursorCodec.PageCursor::lastId).orElse(null);

        List<EventRecordEntity> rows = repository.findPage(
                effectiveFrom,
                effectiveTo,
                lastTimestamp,
                lastId,
                PageRequest.of(0, limit)
        );

        List<LogRecordResponse> responses = new ArrayList<>(rows.size());
        for (EventRecordEntity entity : rows) {
            LogRecord record = toLogRecord(entity);
            cache.ensurePresent(record);
            responses.add(toResponse(record));
        }

        boolean hasMore = rows.size() == limit;
        String nextToken = null;
        if (hasMore) {
            EventRecordEntity last = rows.get(rows.size() - 1);
            nextToken = cursorCodec.encode(new LogCursorCodec.PageCursor(last.getId(), last.getResolvedTimestamp()));
        }

        CacheStatusResponse cacheStatus = buildStatus();
        boolean dataComplete = !hasMore;

        return new LogPageResponse(responses, nextToken, dataComplete, cacheStatus);
    }

    private int resolvePageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        int sanitized = Math.max(1, requested);
        return Math.min(sanitized, MAX_PAGE_SIZE);
    }

    private LogRecord toLogRecord(EventRecordEntity entity) {
        Map<String, String> metadata = parseMetadata(entity.getMetadataJson());
        LogSeverity severity = LogSeverity.valueOf(entity.getSeverity());
        ResolvedEvent.TimestampSource source = ResolvedEvent.TimestampSource.valueOf(entity.getTimestampSource());
        return new LogRecord(
                identityFromEntity(entity),
                entity.getCallerId(),
                entity.getMessage(),
                metadata,
                entity.getResolvedTimestamp(),
                severity,
                source,
                entity.getCorrelationId()
        );
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

    private Map<String, String> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to parse metadata JSON", ex);
        }
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

    private String identityFromEntity(EventRecordEntity entity) {
        return entity.getMessageHash();
    }
}
