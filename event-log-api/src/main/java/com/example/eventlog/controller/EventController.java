package com.example.eventlog.controller;

import com.example.eventlog.model.EventRequest;
import com.example.eventlog.model.EventResponse;
import com.example.eventlog.model.LogPageResponse;
import com.example.eventlog.service.EventReadService;
import com.example.eventlog.service.EventWriteService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventWriteService eventWriteService;
    private final EventReadService eventReadService;

    public EventController(EventWriteService eventWriteService, EventReadService eventReadService) {
        this.eventWriteService = eventWriteService;
        this.eventReadService = eventReadService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> logEvent(
            @Valid @RequestBody EventRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationIdHeader
    ) {
        Optional.ofNullable(correlationIdHeader)
                .filter(id -> !id.isBlank())
                .ifPresent(id -> MDC.put("correlationId", id));
        EventResponse response = eventWriteService.logEvent(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<LogPageResponse> getLogs(
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "pageToken", required = false) String pageToken,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "limit", required = false) Integer legacyLimit,
            @RequestParam(value = "cursor", required = false) String legacyCursor
    ) {
        Integer effectiveSize = pageSize != null ? pageSize : legacyLimit;
        String effectiveToken = pageToken != null ? pageToken : legacyCursor;
        LogPageResponse response = eventReadService.fetchLogs(
                parseInstant(from),
                parseInstant(to),
                effectiveSize,
                effectiveToken
        );
        return ResponseEntity.ok(response);
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("timestamps must be ISO 8601 with timezone");
        }
    }
}
