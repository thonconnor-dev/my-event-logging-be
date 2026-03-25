package com.example.eventlog.service;

import com.example.eventlog.model.ResolvedEvent;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LogIdentityGenerator {

    public String generate(ResolvedEvent event) {
        String payload = event.callerId() + "|" +
                toEpoch(event.resolvedTimestamp()) + "|" +
                event.message() + "|" +
                metadataString(event.metadata());
        byte[] hash = digest().digest(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private String metadataString(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        return metadata.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("|"));
    }

    private long toEpoch(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }

    private MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
