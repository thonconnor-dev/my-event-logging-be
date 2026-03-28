package com.example.eventlog.service;

import com.example.eventlog.model.CacheState;
import com.example.eventlog.model.CacheStatusResponse;
import com.example.eventlog.model.EventRecordEntity;
import com.example.eventlog.model.LogPageResponse;
import com.example.eventlog.repository.EventRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventReadServiceTest {

    private EventRecordRepository repository;
    private TransientLogCache cache;
    private LogCursorCodec cursorCodec;
    private EventReadService service;

    private final Instant fixedNow = Instant.parse("2026-03-27T00:00:00Z");

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(EventRecordRepository.class);
        cache = Mockito.mock(TransientLogCache.class);
        cursorCodec = Mockito.mock(LogCursorCodec.class);
        when(cache.currentState()).thenReturn(CacheState.HEALTHY);
        when(cache.lastRefresh()).thenReturn(fixedNow);
        when(cache.evictionCount()).thenReturn(0L);
        service = new EventReadService(
                repository,
                cache,
                cursorCodec,
                new ObjectMapper(),
                Clock.fixed(fixedNow, ZoneOffset.UTC)
        );
    }

    @Test
    void fetchesRowsAndBuildsResponse() {
        EventRecordEntity entity = sampleEntity();
        when(repository.findPage(any(), any(), any(), any(), any()))
                .thenReturn(List.of(entity));
        when(cursorCodec.decode(null)).thenReturn(Optional.empty());

        LogPageResponse response = service.fetchLogs(null, null, null, null);

        assertThat(response.events()).hasSize(1);
        assertThat(response.nextPageToken()).isNull();
        assertThat(response.cacheStatus()).isNotNull();
        verify(cache).ensurePresent(any());
    }

    @Test
    void passesPaginationParameters() {
        EventRecordEntity entity = sampleEntity();
        when(repository.findPage(any(), any(), any(), any(), any()))
                .thenReturn(List.of(entity, entity));
        when(cursorCodec.decode("token")).thenReturn(Optional.of(new LogCursorCodec.PageCursor(UUID.randomUUID(), fixedNow.minusSeconds(5))));
        when(cursorCodec.encode(any())).thenReturn("next-token");

        LogPageResponse response = service.fetchLogs(fixedNow.minusSeconds(60), fixedNow, 2, "token");

        assertThat(response.nextPageToken()).isEqualTo("next-token");
        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(repository).findPage(
                fromCaptor.capture(),
                toCaptor.capture(),
                any(),
                any(),
                pageRequestCaptor.capture()
        );
        assertThat(fromCaptor.getValue()).isEqualTo(fixedNow.minusSeconds(60));
        assertThat(toCaptor.getValue()).isEqualTo(fixedNow);
        assertThat(pageRequestCaptor.getValue().getPageSize()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidRange() {
        assertThatThrownBy(() -> service.fetchLogs(fixedNow, fixedNow.minusSeconds(1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private EventRecordEntity sampleEntity() {
        return EventRecordEntity.builder()
                .id(UUID.randomUUID())
                .callerId("client-1")
                .severity("INFO")
                .message("Sample")
                .metadataJson("{\"key\":\"value\"}")
                .clientTimestamp(fixedNow.minusSeconds(10))
                .receivedTimestamp(fixedNow.minusSeconds(5))
                .resolvedTimestamp(fixedNow.minusSeconds(5))
                .timestampSource("SERVER")
                .messageHash("hash")
                .status("LOGGED")
                .correlationId("corr-1")
                .expiresAt(fixedNow.plusSeconds(60))
                .build();
    }
}
