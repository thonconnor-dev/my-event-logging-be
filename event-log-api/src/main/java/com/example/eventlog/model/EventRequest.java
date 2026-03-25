package com.example.eventlog.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record EventRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
        String callerId,
        @NotBlank
        @Size(max = 512)
        String message,
        @Size(max = 10)
        Map<String, String> metadata,
        @Size(max = 50)
        @Pattern(regexp = "^(\\d{4}-\\d{2}-\\d{2}T.+)$", message = "timestamp must be ISO 8601")
        String timestamp
) {
}
