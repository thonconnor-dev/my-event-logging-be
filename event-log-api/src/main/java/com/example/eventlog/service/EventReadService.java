package com.example.eventlog.service;

import com.example.eventlog.model.EventRecordEntity;
import com.example.eventlog.model.LogPageResponse;
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
    private final LogCursorCodec cursorCodec;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EventReadService(EventRecordRepository repository,
                            LogCursorCodec cursorCodec,
                            ObjectMapper objectMapper,
                            Clock clock) {
        this.repository = repository;
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

        List<LogRecordResponse> responses = rows.stream()
                .map(this::toResponse)
                .toList();

        boolean hasMore = rows.size() == limit;
        String nextToken = null;
        if (hasMore) {
            EventRecordEntity last = rows.get(rows.size() - 1);
            nextToken = cursorCodec.encode(new LogCursorCodec.PageCursor(last.getId(), last.getResolvedTimestamp()));
        }

        boolean dataComplete = !hasMore;

        return new LogPageResponse(responses, nextToken, dataComplete);
    }

    private int resolvePageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        int sanitized = Math.max(1, requested);
        return Math.min(sanitized, MAX_PAGE_SIZE);
    }

    private LogRecordResponse toResponse(EventRecordEntity entity) {
        Map<String, String> metadata = parseMetadata(entity.getMetadataJson());
        LogSeverity severity = LogSeverity.valueOf(entity.getSeverity());
        ResolvedEvent.TimestampSource source = ResolvedEvent.TimestampSource.valueOf(entity.getTimestampSource());
        return new LogRecordResponse(
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

    private String identityFromEntity(EventRecordEntity entity) {
        return entity.getMessageHash();
    }
}
