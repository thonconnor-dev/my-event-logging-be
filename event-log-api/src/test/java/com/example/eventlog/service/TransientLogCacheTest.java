package com.example.eventlog.service;

import com.example.eventlog.config.LogCacheProperties;
import com.example.eventlog.model.CacheState;
import com.example.eventlog.model.LogRecord;
import com.example.eventlog.model.LogSeverity;
import com.example.eventlog.model.ResolvedEvent.TimestampSource;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransientLogCacheTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-25T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void upsertMaintainsOrderingAndDeduplication() {
        TransientLogCache cache = new TransientLogCache(new LogCacheProperties(10, 60, 200), FIXED_CLOCK);
        LogRecord first = record("a", 0);
        LogRecord second = record("b", 1);
        LogRecord updatedFirst = record("a", 2);

        cache.upsert(first);
        cache.upsert(second);
        cache.upsert(updatedFirst);

        List<LogRecord> snapshot = cache.snapshot();
        assertThat(snapshot).hasSize(2);
        assertThat(snapshot.get(0).id()).isEqualTo(second.id());
        assertThat(snapshot.get(1).id()).isEqualTo(updatedFirst.id());
    }

    @Test
    void trimsToCapacityAndTracksEvictions() {
        TransientLogCache cache = new TransientLogCache(new LogCacheProperties(1, 60, 200), FIXED_CLOCK);

        cache.upsert(record("a", 0));
        cache.upsert(record("b", 1));

        assertThat(cache.snapshot()).hasSize(1);
        assertThat(cache.snapshot().get(0).id()).isEqualTo("b");
        assertThat(cache.evictionCount()).isEqualTo(1);
    }

    @Test
    void reportsCacheState() {
        Clock clock = Clock.tick(FIXED_CLOCK, Duration.ofSeconds(1));
        TransientLogCache cache = new TransientLogCache(new LogCacheProperties(2, 1, 200), clock);

        assertThat(cache.currentState()).isEqualTo(CacheState.EMPTY);

        cache.upsert(record("a", 0));
        assertThat(cache.currentState()).isEqualTo(CacheState.HEALTHY);

        // trigger eviction to mark truncated
        cache.upsert(record("b", 1));
        cache.upsert(record("c", 2));
        assertThat(cache.currentState()).isEqualTo(CacheState.TRUNCATED);
    }

    private LogRecord record(String id, int seconds) {
        return new LogRecord(
                id,
                "caller-" + id,
                "msg-" + id,
                Map.of("severity", "INFO"),
                Instant.ofEpochSecond(seconds),
                LogSeverity.INFO,
                TimestampSource.SERVER,
                "corr-" + id
        );
    }
}
