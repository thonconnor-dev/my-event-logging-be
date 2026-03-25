package com.example.eventlog.service;

import com.example.eventlog.model.CacheState;
import com.example.eventlog.model.LogRecord;
import com.example.eventlog.model.LogSeverity;
import com.example.eventlog.model.ResolvedEvent.TimestampSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogQueryServiceTest {

    private TransientLogCache cache;
    private LogCursorCodec cursorCodec;
    private LogQueryService service;

    @BeforeEach
    void setUp() {
        cache = Mockito.mock(TransientLogCache.class);
        cursorCodec = Mockito.mock(LogCursorCodec.class);
        service = new LogQueryService(cache, cursorCodec, Clock.fixed(Instant.parse("2026-03-25T12:00:00Z"), ZoneOffset.UTC));
        when(cache.maxPageSize()).thenReturn(200);
        when(cache.currentState()).thenReturn(CacheState.HEALTHY);
        when(cache.lastRefresh()).thenReturn(Instant.parse("2026-03-25T11:59:00Z"));
        when(cache.evictionCount()).thenReturn(0L);
        when(cursorCodec.decode(null)).thenReturn(Optional.empty());
    }

    @Test
    void fetchLogsOrdersByTimestampAndProvidesCursor() {
        LogRecord newest = record("newest", Instant.parse("2026-03-25T11:00:00Z"));
        LogRecord oldest = record("oldest", Instant.parse("2026-03-24T11:00:00Z"));
        when(cache.snapshot()).thenReturn(List.of(oldest, newest));
        when(cursorCodec.encode("newest")).thenReturn("cursor-newest");

        var response = service.fetchLogs(1, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).id()).isEqualTo("newest");
        assertThat(response.nextCursor()).isEqualTo("cursor-newest");
        verify(cache, times(1)).ensurePresent(any(LogRecord.class));
    }

    @Test
    void fetchLogsBackfillsMissingEntries() {
        LogRecord first = record("a", Instant.parse("2026-03-25T10:00:00Z"));
        LogRecord second = record("b", Instant.parse("2026-03-25T09:00:00Z"));
        when(cache.snapshot()).thenReturn(List.of(first, second));
        when(cursorCodec.encode("b")).thenReturn("cursor-b");

        service.fetchLogs(2, null);

        ArgumentCaptor<LogRecord> captor = ArgumentCaptor.forClass(LogRecord.class);
        verify(cache, times(2)).ensurePresent(captor.capture());
        assertThat(captor.getAllValues()).extracting(LogRecord::id).containsExactly("a", "b");
    }

    private LogRecord record(String id, Instant timestamp) {
        return new LogRecord(
                id,
                "caller-" + id,
                "message-" + id,
                Map.of(),
                timestamp,
                LogSeverity.INFO,
                TimestampSource.SERVER,
                "corr-" + id
        );
    }
}
