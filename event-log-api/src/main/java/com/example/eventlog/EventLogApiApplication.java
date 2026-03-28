package com.example.eventlog;

import com.example.eventlog.config.LogCacheProperties;
import com.example.eventlog.config.RetentionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({LogCacheProperties.class, RetentionProperties.class})
public class EventLogApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventLogApiApplication.class, args);
    }
}
