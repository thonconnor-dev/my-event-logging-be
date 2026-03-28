package com.example.eventlog.service;

import com.example.eventlog.config.RetentionProperties;
import com.example.eventlog.model.EventRecordEntity;
import com.example.eventlog.model.EventRequest;
import com.example.eventlog.model.EventResponse;
import com.example.eventlog.model.LogRecord;
import com.example.eventlog.model.LogSeverity;
import com.example.eventlog.model.ResolvedEvent;
import com.example.eventlog.repository.EventRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EventWriteService extends PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(EventWriteService.class);
    private static final Duration MAX_FUTURE_DRIFT = Duration.ofHours(24);

    private final LogIdentityGenerator identityGenerator;
    private final TransientLogCache transientLogCache;
    private final ObjectMapper objectMapper;
    private final RetentionProperties retentionProperties;

    public EventWriteService(
            Clock clock,
            LogIdentityGenerator identityGenerator,
            TransientLogCache transientLogCache,
            EventRecordRepository repository,
            ObjectMapper objectMapper,
            RetentionProperties retentionProperties
    ) {
        super(repository, clock);
        this.identityGenerator = identityGenerator;
        this.transientLogCache = transientLogCache;
        this.objectMapper = objectMapper;
        this.retentionProperties = retentionProperties;
    }

    public EventResponse logEvent(EventRequest request) {
        Instant now = Instant.now(clock);
        String correlationId = ensureCorrelationId();
        Map<String, String> metadata = sanitizeMetadata(request.metadata());
        ResolvedEvent resolvedEvent = resolveEvent(request, metadata, now, correlationId);
        String messageHash = identityGenerator.generate(resolvedEvent);

        Optional<EventRecordEntity> existingRecord = repository.findFirstByCallerIdAndResolvedTimestampAndMessageHash(
                request.callerId(),
                resolvedEvent.resolvedTimestamp(),
                messageHash
        );
        if (existingRecord.isPresent()) {
            transientLogCache.ensurePresent(toLogRecord(resolvedEvent));
            logDuplicate(resolvedEvent);
            EventRecordEntity existing = existingRecord.get();
            return EventResponse.success(existing.getResolvedTimestamp().toString(), correlationId, existing.getId().toString());
        }

        EventRecordEntity entity = EventRecordEntity.builder()
                .id(UUID.randomUUID())
                .callerId(request.callerId())
                .severity(resolveSeverity(metadata).name())
                .message(resolvedEvent.message())
                .metadataJson(writeMetadata(metadata))
                .clientTimestamp(parseOptionalTimestamp(request.timestamp()).map(OffsetDateTime::toInstant).orElse(null))
                .receivedTimestamp(now)
                .resolvedTimestamp(resolvedEvent.resolvedTimestamp())
                .timestampSource(resolvedEvent.source().name())
                .messageHash(messageHash)
                .status("LOGGED")
                .correlationId(correlationId)
                .expiresAt(resolvedEvent.resolvedTimestamp().plus(Duration.ofDays(retentionProperties.days())))
                .build();

        try {
            repository.saveAndFlush(entity);
            transientLogCache.upsert(toLogRecord(resolvedEvent));
            logEventLine(resolvedEvent);
            return EventResponse.success(resolvedEvent.resolvedTimestamp().toString(), correlationId, entity.getId().toString());
        } catch (RuntimeException ex) {
            if (isUniqueConstraintViolation(ex)) {
                EventRecordEntity existing = repository.findFirstByCallerIdAndResolvedTimestampAndMessageHash(
                        resolvedEvent.callerId(),
                        resolvedEvent.resolvedTimestamp(),
                        messageHash
                ).orElseThrow(() -> ex);
                transientLogCache.ensurePresent(toLogRecord(resolvedEvent));
                logDuplicate(resolvedEvent);
                return EventResponse.success(existing.getResolvedTimestamp().toString(), correlationId, existing.getId().toString());
            }
            throw ex;
        }
    }

    private ResolvedEvent resolveEvent(
            EventRequest request,
            Map<String, String> metadata,
            Instant now,
            String correlationId
    ) {
        Instant resolvedInstant;
        ResolvedEvent.TimestampSource source;
        Optional<OffsetDateTime> provided = parseOptionalTimestamp(request.timestamp());
        if (provided.isEmpty()) {
            resolvedInstant = now;
            source = ResolvedEvent.TimestampSource.SERVER;
        } else {
            OffsetDateTime parsed = provided.get();
            resolvedInstant = parsed.toInstant();
            if (resolvedInstant.isAfter(now.plus(MAX_FUTURE_DRIFT))) {
                throw new IllegalArgumentException("timestamp must be within 24h of server time");
            }
            source = ResolvedEvent.TimestampSource.CLIENT;
        }

        return new ResolvedEvent(
                request.callerId(),
                request.message().trim(),
                metadata,
                resolvedInstant,
                source,
                correlationId
        );
    }

    private Optional<OffsetDateTime> parseOptionalTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
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
            sanitized.put(key.trim(), value.trim());
        });
        return Map.copyOf(sanitized);
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

    private String writeMetadata(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize metadata", e);
        }
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

    private void logDuplicate(ResolvedEvent event) {
        log.info("eventlog callerId={} status=duplicate timestamp={} corr={} message=\"{}\"",
                event.callerId(),
                event.resolvedTimestamp(),
                event.correlationId(),
                event.message());
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

    private boolean isUniqueConstraintViolation(Throwable throwable) {
        if (throwable instanceof DataIntegrityViolationException) {
            return true;
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
