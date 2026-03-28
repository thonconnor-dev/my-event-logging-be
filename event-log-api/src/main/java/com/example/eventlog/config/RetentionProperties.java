package com.example.eventlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eventlog.retention")
public record RetentionProperties(
        int days,
        String cron,
        int batchSize
) {
}
