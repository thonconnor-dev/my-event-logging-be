package com.example.eventlog.service;

import com.example.eventlog.config.RetentionProperties;
import com.example.eventlog.repository.EventRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final EventRecordRepository repository;
    private final RetentionProperties properties;
    private final Clock clock;

    public RetentionJob(EventRecordRepository repository, RetentionProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${eventlog.retention.cron}")
    public void purgeExpired() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(properties.days()));
        long deleted = repository.deleteByResolvedTimestampBefore(cutoff);
        if (deleted > 0) {
            log.info("Retention job removed {} expired event records older than {}", deleted, cutoff);
        }
    }
}
