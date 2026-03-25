package com.example.eventlog.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "log.cache")
public class LogCacheProperties {

    private final int capacity;
    private final int stalenessSeconds;
    private final int maxPageSize;

    public LogCacheProperties(
            @DefaultValue("500") @Min(1) @Max(2000) int capacity,
            @DefaultValue("60") @Min(1) int stalenessSeconds,
            @DefaultValue("200") @Min(1) @Max(1000) int maxPageSize
    ) {
        this.capacity = capacity;
        this.stalenessSeconds = stalenessSeconds;
        this.maxPageSize = maxPageSize;
    }

    public int capacity() {
        return capacity;
    }

    public int stalenessSeconds() {
        return stalenessSeconds;
    }

    public int maxPageSize() {
        return maxPageSize;
    }
}
