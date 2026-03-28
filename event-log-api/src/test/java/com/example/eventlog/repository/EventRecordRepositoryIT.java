package com.example.eventlog.repository;

import com.example.eventlog.model.EventRecordEntity;
import com.example.eventlog.support.EventRecordFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventRecordRepositoryIT {

    private static final Path TEMP_DIR;

    static {
        try {
            TEMP_DIR = Files.createTempDirectory("eventlog-repo-sqlite");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void configureDb(DynamicPropertyRegistry registry) {
        registry.add("EVENT_LOG_DB_PATH", () -> TEMP_DIR.resolve("event-log.db").toString());
    }

    @Autowired
    private EventRecordRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void findsPagesInDeterministicOrder() {
        Instant oldest = Instant.parse("2026-02-28T00:00:00Z");
        Instant middle = Instant.parse("2026-03-01T12:00:00Z");
        Instant newest = Instant.parse("2026-03-02T00:00:00Z");

        repository.saveAll(List.of(
                record("event-oldest", oldest, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                record("event-middle", middle, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                record("event-newest", newest, UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"))
        ));

        PageRequest page = PageRequest.of(0, 2);
        List<EventRecordEntity> firstPage = repository.findPage(
                oldest.minus(1, ChronoUnit.DAYS),
                newest.plus(1, ChronoUnit.HOURS),
                null,
                null,
                page
        );

        assertThat(firstPage)
                .extracting(EventRecordEntity::getMessage)
                .containsExactly("event-newest", "event-middle");

        EventRecordEntity lastFromFirstPage = firstPage.get(firstPage.size() - 1);
        List<EventRecordEntity> secondPage = repository.findPage(
                oldest.minus(1, ChronoUnit.DAYS),
                newest.plus(1, ChronoUnit.HOURS),
                lastFromFirstPage.getResolvedTimestamp(),
                lastFromFirstPage.getId(),
                page
        );

        assertThat(secondPage)
                .extracting(EventRecordEntity::getMessage)
                .containsExactly("event-oldest");
    }

    private EventRecordEntity record(String message, Instant resolvedTimestamp, UUID id) {
        return EventRecordFixtures.builder()
                .id(id)
                .message(message)
                .resolvedTimestamp(resolvedTimestamp)
                .receivedTimestamp(resolvedTimestamp)
                .clientTimestamp(resolvedTimestamp)
                .expiresAt(resolvedTimestamp.plus(30, ChronoUnit.DAYS))
                .messageHash("hash-" + message)
                .build();
    }
}
