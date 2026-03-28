package com.example.eventlog.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyRetentionConfigValidatorTest {

    @Test
    void throwsWhenLegacyPropertiesPresent() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("eventlog.retention.days", "30")
                .withProperty("eventlog.retention.cron", "0 0 2 * * *");

        LegacyRetentionConfigValidator validator = new LegacyRetentionConfigValidator(environment);

        assertThatThrownBy(validator::validateLegacyRetentionSettings)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eventlog.retention");
    }

    @Test
    void passesWhenLegacyPropertiesAbsent() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("eventlog.foo", "bar");

        LegacyRetentionConfigValidator validator = new LegacyRetentionConfigValidator(environment);

        assertThatCode(validator::validateLegacyRetentionSettings).doesNotThrowAnyException();
    }
}
