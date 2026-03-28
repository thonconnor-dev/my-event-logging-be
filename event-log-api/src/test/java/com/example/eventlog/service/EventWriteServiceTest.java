package com.example.eventlog.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.eventlog.config.RetentionProperties;
import com.example.eventlog.model.EventRecordEntity;
import com.example.eventlog.model.EventRequest;
import com.example.eventlog.model.EventResponse;
import com.example.eventlog.repository.EventRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventWriteServiceTest {

    private EventWriteService service;
    private EventRecordRepository repository;
    private TransientLogCache transientLogCache;
    private LogIdentityGenerator identityGenerator;
    private ListAppender<ILoggingEvent> appender;

    private final Instant fixedInstant = Instant.parse("2026-03-24T16:00:00Z");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        repository = Mockito.mock(EventRecordRepository.class);
        transientLogCache = Mockito.mock(TransientLogCache.class);
        identityGenerator = Mockito.mock(LogIdentityGenerator.class);
        when(identityGenerator.generate(any())).thenReturn("hash");

        service = new EventWriteService(
                clock,
                identityGenerator,
                transientLogCache,
                repository,
                new ObjectMapper(),
                new RetentionProperties(30, "0 0 2 * * *", 500)
        );

        Logger logger = (Logger) LoggerFactory.getLogger(EventWriteService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(EventWriteService.class);
        logger.detachAppender(appender);
    }

    @Test
    void persistsEventAndReturnsId() {
        EventRequest request = new EventRequest("client-1", "Daily sync", Collections.emptyMap(), null);
        when(repository.findFirstByCallerIdAndResolvedTimestampAndMessageHash(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(EventRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = service.logEvent(request);

        assertThat(response.success()).isTrue();
        assertThat(response.timestamp()).isEqualTo(fixedInstant.toString());
        assertThat(response.eventId()).isNotBlank();

        ArgumentCaptor<EventRecordEntity> captor = ArgumentCaptor.forClass(EventRecordEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCallerId()).isEqualTo("client-1");
        verify(transientLogCache).upsert(any());
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("status=logged");
    }

    @Test
    void dedupesWhenRecordAlreadyExists() {
        EventRequest request = new EventRequest("client-1", "Daily sync", Collections.emptyMap(), null);
        EventRecordEntity existing = EventRecordEntity.builder()
                .id(UUID.randomUUID())
                .callerId("client-1")
                .severity("INFO")
                .message("Daily sync")
                .metadataJson("{}")
                .clientTimestamp(null)
                .receivedTimestamp(fixedInstant)
                .resolvedTimestamp(fixedInstant)
                .timestampSource("SERVER")
                .messageHash("hash")
                .status("LOGGED")
                .correlationId("corr")
                .expiresAt(fixedInstant.plusSeconds(1))
                .build();
        when(repository.findFirstByCallerIdAndResolvedTimestampAndMessageHash(any(), any(), any()))
                .thenReturn(Optional.of(existing));

        EventResponse response = service.logEvent(request);

        assertThat(response.eventId()).isEqualTo(existing.getId().toString());
        verify(repository, never()).saveAndFlush(any());
        verify(transientLogCache, never()).upsert(any());
        verify(transientLogCache).ensurePresent(any());
    }

    @Test
    void dedupesWhenConstraintTriggersDuringSave() {
        EventRequest request = new EventRequest("client-1", "Daily sync", Collections.emptyMap(), null);
        EventRecordEntity existing = EventRecordEntity.builder()
                .id(UUID.randomUUID())
                .callerId("client-1")
                .severity("INFO")
                .message("Daily sync")
                .metadataJson("{}")
                .clientTimestamp(null)
                .receivedTimestamp(fixedInstant)
                .resolvedTimestamp(fixedInstant)
                .timestampSource("SERVER")
                .messageHash("hash")
                .status("LOGGED")
                .correlationId("corr")
                .expiresAt(fixedInstant.plusSeconds(1))
                .build();
        when(repository.findFirstByCallerIdAndResolvedTimestampAndMessageHash(any(), any(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(EventRecordEntity.class))).thenThrow(new DataIntegrityViolationException("dupe"));

        EventResponse response = service.logEvent(request);

        assertThat(response.eventId()).isEqualTo(existing.getId().toString());
        verify(transientLogCache).ensurePresent(any());
    }

    @Test
    void rejectsTimestampsTooFarAhead() {
        EventRequest request = new EventRequest("client-1", "Future", Collections.emptyMap(), "2030-01-01T00:00:00Z");
        assertThatThrownBy(() -> service.logEvent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("within 24h");
    }
}
