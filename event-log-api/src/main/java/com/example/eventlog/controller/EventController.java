package com.example.eventlog.controller;

import com.example.eventlog.model.EventRequest;
import com.example.eventlog.model.EventResponse;
import com.example.eventlog.service.EventLogService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventLogService eventLogService;

    public EventController(EventLogService eventLogService) {
        this.eventLogService = eventLogService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> logEvent(
            @Valid @RequestBody EventRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationIdHeader
    ) {
        Optional.ofNullable(correlationIdHeader)
                .filter(id -> !id.isBlank())
                .ifPresent(id -> MDC.put("correlationId", id));
        EventResponse response = eventLogService.logEvent(request);
        return ResponseEntity.ok(response);
    }
}
