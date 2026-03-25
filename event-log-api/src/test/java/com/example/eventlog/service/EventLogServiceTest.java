package com.example.eventlog.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.eventlog.model.EventRequest;
import com.example.eventlog.model.EventResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class EventLogServiceTest {

    private EventLogService service;
    private ListAppender<ILoggingEvent> appender;
    private final Instant fixedInstant = Instant.parse("2026-03-24T16:00:00Z");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        service = new EventLogService(clock);
        Logger logger = (Logger) LoggerFactory.getLogger(EventLogService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(EventLogService.class);
        logger.detachAppender(appender);
    }

    @Test
    void logsServerTimestampWhenMissing() {
        EventRequest request = new EventRequest("client-1", "Daily sync", Collections.emptyMap(), null);

        EventResponse response = service.logEvent(request);

        assertThat(response.success()).isTrue();
        assertThat(response.timestamp()).isEqualTo(fixedInstant.toString());
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("callerId=client-1")
                .contains("status=logged")
                .contains("timestamp=" + fixedInstant);
    }

    @Test
    void usesClientTimestampWhenProvided() {
        String clientTimestamp = "2026-03-24T10:00:00-07:00";
        EventRequest request = new EventRequest("client-1", "From device", Collections.emptyMap(), clientTimestamp);

        EventResponse response = service.logEvent(request);

        assertThat(response.timestamp()).isEqualTo("2026-03-24T17:00:00Z");
        assertThat(appender.list.get(0).getFormattedMessage()).contains("source=client");
    }

    @Test
    void rejectsTimestampTooFarInFuture() {
        String future = "2026-03-26T17:01:00Z";
        EventRequest request = new EventRequest("client-1", "Future", Collections.emptyMap(), future);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.logEvent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("within 24h");
    }
}
