package com.example.eventlog;

import com.example.eventlog.config.LogCacheProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LogCacheProperties.class)
public class EventLogApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventLogApiApplication.class, args);
    }
}
