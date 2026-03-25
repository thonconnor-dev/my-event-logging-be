package com.example.eventlog.service;

import com.example.eventlog.config.LogCacheProperties;
import com.example.eventlog.model.CacheState;
import com.example.eventlog.model.LogRecord;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TransientLogCache {
    private final ConcurrentLinkedDeque<LogRecord> queue = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, LogRecord> index = new ConcurrentHashMap<>();
    private final int capacity;
    private final int maxPageSize;
    private final Duration staleThreshold;
    private final Clock clock;
    private final AtomicLong evictionCount = new AtomicLong(0);
    private volatile Instant lastRefresh = Instant.EPOCH;

    public TransientLogCache(LogCacheProperties properties, Clock clock) {
        this.capacity = properties.capacity();
        this.maxPageSize = properties.maxPageSize();
        this.staleThreshold = Duration.ofSeconds(properties.stalenessSeconds());
        this.clock = clock;
    }

    public synchronized void upsert(LogRecord record) {
        LogRecord existing = index.put(record.id(), record);
        if (existing != null) {
            queue.removeIf(entry -> entry.id().equals(record.id()));
        }
        queue.addLast(record);
        trimToCapacity();
        lastRefresh = Instant.now(clock);
    }

    public synchronized void ensurePresent(LogRecord record) {
        if (!index.containsKey(record.id())) {
            queue.addLast(record);
            index.put(record.id(), record);
            trimToCapacity();
            lastRefresh = Instant.now(clock);
        }
    }

    public List<LogRecord> snapshot() {
        return new ArrayList<>(queue);
    }

    public Optional<LogRecord> find(String id) {
        return Optional.ofNullable(index.get(id));
    }

    public int capacity() {
        return capacity;
    }

    public int maxPageSize() {
        return maxPageSize;
    }

    public long evictionCount() {
        return evictionCount.get();
    }

    public Instant lastRefresh() {
        return lastRefresh;
    }

    public CacheState currentState() {
        if (queue.isEmpty()) {
            return CacheState.EMPTY;
        }
        if (evictionCount.get() > 0) {
            return CacheState.TRUNCATED;
        }
        if (Duration.between(lastRefresh, Instant.now(clock)).compareTo(staleThreshold) > 0) {
            return CacheState.STALE;
        }
        return CacheState.HEALTHY;
    }

    private void trimToCapacity() {
        while (queue.size() > capacity) {
            LogRecord removed = queue.pollFirst();
            if (removed != null) {
                index.remove(removed.id(), removed);
                evictionCount.incrementAndGet();
            }
        }
    }
}
