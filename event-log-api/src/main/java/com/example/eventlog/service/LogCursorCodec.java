package com.example.eventlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

@Component
public class LogCursorCodec {

    private final ObjectMapper objectMapper;

    public LogCursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<PageCursor> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] raw = Base64.getUrlDecoder().decode(cursor);
            CursorPayload payload = objectMapper.readValue(raw, CursorPayload.class);
            if (payload.lastId() == null || payload.lastId().isBlank() || payload.lastTimestamp() == null) {
                return Optional.empty();
            }
            UUID id = UUID.fromString(payload.lastId());
            Instant timestamp = Instant.parse(payload.lastTimestamp());
            return Optional.of(new PageCursor(id, timestamp));
        } catch (IllegalArgumentException | IOException ex) {
            return Optional.empty();
        }
    }

    public String encode(PageCursor cursor) {
        if (cursor == null || cursor.lastId() == null || cursor.lastResolvedTimestamp() == null) {
            throw new IllegalArgumentException("cursor must include id and timestamp");
        }
        try {
            CursorPayload payload = new CursorPayload(cursor.lastId().toString(), cursor.lastResolvedTimestamp().toString());
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().encodeToString(json);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode cursor", ex);
        }
    }

    private record CursorPayload(String lastId, String lastTimestamp) {
    }

    public record PageCursor(UUID lastId, Instant lastResolvedTimestamp) {
    }
}
