package com.example.eventlog.controller;

import com.example.eventlog.model.EventRequest;
import com.example.eventlog.model.EventResponse;
import com.example.eventlog.service.EventLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventLogService eventLogService;

    @Test
    void returnsServerTimestampWhenMissing() throws Exception {
        EventResponse response = EventResponse.success("2026-03-24T16:00:00Z", "corr-123");
        Mockito.when(eventLogService.logEvent(any(EventRequest.class))).thenReturn(response);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EventRequest("client-1", "Daily sync", Collections.emptyMap(), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").value("2026-03-24T16:00:00Z"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void rejectsInvalidTimestamp() throws Exception {
        Mockito.when(eventLogService.logEvent(any(EventRequest.class)))
                .thenThrow(new IllegalArgumentException("timestamp must be ISO 8601 with timezone"));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EventRequest("client-1", "oops", Collections.emptyMap(), "tomorrow"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0]").value("timestamp must be ISO 8601 with timezone"));
    }
}
