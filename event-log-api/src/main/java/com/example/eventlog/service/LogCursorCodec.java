package com.example.eventlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

@Component
public class LogCursorCodec {

    private final ObjectMapper objectMapper;

    public LogCursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<String> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] raw = Base64.getUrlDecoder().decode(cursor);
            CursorPayload payload = objectMapper.readValue(raw, CursorPayload.class);
            return Optional.ofNullable(payload.lastId()).filter(id -> !id.isBlank());
        } catch (IllegalArgumentException | IOException ex) {
            return Optional.empty();
        }
    }

    public String encode(String lastId) {
        if (lastId == null || lastId.isBlank()) {
            throw new IllegalArgumentException("lastId must be provided");
        }
        try {
            byte[] json = objectMapper.writeValueAsBytes(new CursorPayload(lastId));
            return Base64.getUrlEncoder().encodeToString(json);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode cursor", ex);
        }
    }

    private record CursorPayload(String lastId) {
    }
}
