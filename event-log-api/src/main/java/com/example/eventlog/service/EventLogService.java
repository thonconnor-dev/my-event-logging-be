package com.example.eventlog.service;

import com.example.eventlog.model.EventRequest;
import com.example.eventlog.model.EventResponse;
import com.example.eventlog.model.LogRecord;
import com.example.eventlog.model.LogSeverity;
import com.example.eventlog.model.ResolvedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EventLogService {

    private static final Logger log = LoggerFactory.getLogger(EventLogService.class);
    private static final Duration MAX_FUTURE_DRIFT = Duration.ofHours(24);

    private final Clock clock;
    private final LogIdentityGenerator identityGenerator;
    private final TransientLogCache transientLogCache;

    public EventLogService(Clock clock, LogIdentityGenerator identityGenerator, TransientLogCache transientLogCache) {
        this.clock = clock;
        this.identityGenerator = identityGenerator;
        this.transientLogCache = transientLogCache;
    }

    public EventResponse logEvent(EventRequest request) {
        Instant now = Instant.now(clock);
        String correlationId = ensureCorrelationId();
        ResolvedEvent resolved = resolveEvent(request, now, correlationId);
        logEventLine(resolved);
        transientLogCache.upsert(toLogRecord(resolved));
        return EventResponse.success(resolved.resolvedTimestamp().toString(), correlationId);
    }

    private ResolvedEvent resolveEvent(EventRequest request, Instant now, String correlationId) {
        Map<String, String> metadata = sanitizeMetadata(request.metadata());
        String message = request.message().trim();
        String callerId = request.callerId();

        Instant resolvedInstant;
        ResolvedEvent.TimestampSource source;
        if (request.timestamp() == null || request.timestamp().isBlank()) {
            resolvedInstant = now;
            source = ResolvedEvent.TimestampSource.SERVER;
        } else {
            OffsetDateTime parsed = parseTimestamp(request.timestamp());
            resolvedInstant = parsed.toInstant();
            if (resolvedInstant.isAfter(now.plus(MAX_FUTURE_DRIFT))) {
                throw new IllegalArgumentException("timestamp must be within 24h of server time");
            }
            source = ResolvedEvent.TimestampSource.CLIENT;
        }

        return new ResolvedEvent(
                callerId,
                message,
                metadata,
                resolvedInstant,
                source,
                correlationId
        );
    }

    private LogRecord toLogRecord(ResolvedEvent event) {
        return new LogRecord(
                identityGenerator.generate(event),
                event.callerId(),
                event.message(),
                event.metadata(),
                event.resolvedTimestamp(),
                resolveSeverity(event.metadata()),
                event.source(),
                event.correlationId()
        );
    }

    private LogSeverity resolveSeverity(Map<String, String> metadata) {
        if (metadata == null) {
            return LogSeverity.INFO;
        }
        String candidate = metadata.get("severity");
        if (candidate == null) {
            return LogSeverity.INFO;
        }
        try {
            return LogSeverity.valueOf(candidate.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return LogSeverity.INFO;
        }
    }

    private OffsetDateTime parseTimestamp(String timestamp) {
        try {
            return OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("timestamp must be ISO 8601 with timezone");
        }
    }

    private Map<String, String> sanitizeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        if (metadata.size() > 10) {
            throw new IllegalArgumentException("metadata supports up to 10 entries");
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("metadata keys may not be blank");
            }
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("metadata values may not be blank");
            }
            if (value.length() > 128) {
                throw new IllegalArgumentException("metadata values must be <= 128 characters");
            }
            sanitized.put(key, value);
        });
        return Map.copyOf(sanitized);
    }

    private void logEventLine(ResolvedEvent event) {
        String metadata = event.metadata().isEmpty()
                ? ""
                : " metadata=" + event.metadata().entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
        log.info("eventlog callerId={} status=logged timestamp={} corr={} source={} message=\"{}\"{}",
                event.callerId(),
                event.resolvedTimestamp(),
                event.correlationId(),
                event.source().name().toLowerCase(),
                event.message(),
                metadata);
    }

    private String ensureCorrelationId() {
        String existing = MDC.get("correlationId");
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String generated = UUID.randomUUID().toString();
        MDC.put("correlationId", generated);
        return generated;
    }
}
