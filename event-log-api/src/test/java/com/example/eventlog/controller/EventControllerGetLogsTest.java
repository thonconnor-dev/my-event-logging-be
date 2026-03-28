package com.example.eventlog.controller;

import com.example.eventlog.config.ApiExceptionHandler;
import com.example.eventlog.model.CacheState;
import com.example.eventlog.model.CacheStatusResponse;
import com.example.eventlog.model.LogPageResponse;
import com.example.eventlog.model.LogRecordResponse;
import com.example.eventlog.model.LogSeverity;
import com.example.eventlog.model.ResolvedEvent.TimestampSource;
import com.example.eventlog.service.EventReadService;
import com.example.eventlog.service.EventWriteService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EventController.class)
@Import(ApiExceptionHandler.class)
class EventControllerGetLogsTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private EventReadService eventReadService;

        @MockBean
        private EventWriteService eventWriteService;

        @Test
        void returnsLogsWithMetadata() throws Exception {
                LogRecordResponse item = new LogRecordResponse("abc", "caller-1", "message",
                                Map.of("severity", "INFO"), Instant.parse("2026-03-25T10:00:00Z"),
                                LogSeverity.INFO, TimestampSource.SERVER, "corr-1");
                CacheStatusResponse statusResponse = new CacheStatusResponse(CacheState.HEALTHY,
                                Instant.parse("2026-03-25T10:01:00Z"), 0, 5);
                Mockito.when(eventReadService.fetchLogs(null, null, null, null))
                                .thenReturn(new LogPageResponse(List.of(item), "cursor123", true,
                                                statusResponse));

                mockMvc.perform(get("/events").accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.events[0].id").value("abc"))
                                .andExpect(jsonPath("$.nextPageToken").value("cursor123"))
                                .andExpect(jsonPath("$.cacheStatus.state").value("HEALTHY"))
                                .andExpect(jsonPath("$.dataComplete").value(true));
        }

        @Test
        void passesLimitAndCursorParamsToService() throws Exception {
                String from = "2026-03-01T00:00:00Z";
                String to = "2026-03-02T00:00:00Z";
                Mockito.when(eventReadService.fetchLogs(Instant.parse(from), Instant.parse(to), 25,
                                "token-1"))
                                .thenReturn(new LogPageResponse(List.of(), null, true, null));

                mockMvc.perform(get("/events").param("from", from).param("to", to)
                                .param("pageSize", "25").param("pageToken", "token-1"))
                                .andExpect(status().isOk());

                verify(eventReadService).fetchLogs(eq(Instant.parse(from)), eq(Instant.parse(to)),
                                eq(25), eq("token-1"));
        }

        @Test
        void reusesCursorForSubsequentPages() throws Exception {
                Mockito.when(eventReadService.fetchLogs(null, null, null, null))
                                .thenReturn(new LogPageResponse(List.of(), "cursor-1", true, null));
                Mockito.when(eventReadService.fetchLogs(null, null, null, "cursor-1"))
                                .thenReturn(new LogPageResponse(List.of(), null, true, null));

                mockMvc.perform(get("/events")).andExpect(status().isOk());

                mockMvc.perform(get("/events").param("cursor", "cursor-1"))
                                .andExpect(status().isOk());

                verify(eventReadService).fetchLogs(null, null, null, "cursor-1");
        }
}
