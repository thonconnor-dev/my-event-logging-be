package com.example.eventlog.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fails startup if any of the legacy {@code eventlog.retention.*} properties remain configured.
 */
@Component
public class LegacyRetentionConfigValidator {

    private final Environment environment;

    public LegacyRetentionConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void checkForLegacyProperties() {
        validateLegacyRetentionSettings();
    }

    void validateLegacyRetentionSettings() {
        Binder binder = Binder.get(environment);
        BindResult<Map<String, Object>> result =
                binder.bind("eventlog.retention", Bindable.mapOf(String.class, Object.class));
        if (result.isBound() && !result.get().isEmpty()) {
            String keys = result.get().keySet().stream().collect(Collectors.joining(", "));
            throw new IllegalStateException(
                    "Remove obsolete eventlog.retention.* settings (" + keys + "); retention now handled externally.");
        }
    }
}
