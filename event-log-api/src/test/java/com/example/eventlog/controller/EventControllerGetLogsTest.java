package com.example.eventlog.controller;

import com.example.eventlog.config.ApiExceptionHandler;
import com.example.eventlog.model.CacheState;
import com.example.eventlog.model.CacheStatusResponse;
import com.example.eventlog.model.LogPageResponse;
import com.example.eventlog.model.LogRecordResponse;
import com.example.eventlog.model.LogSeverity;
import com.example.eventlog.model.ResolvedEvent.TimestampSource;
import com.example.eventlog.service.EventLogService;
import com.example.eventlog.service.LogQueryService;
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
    private LogQueryService logQueryService;

    @MockBean
    private EventLogService eventLogService;

    @Test
    void returnsLogsWithMetadata() throws Exception {
        LogRecordResponse item = new LogRecordResponse(
                "abc",
                "caller-1",
                "message",
                Map.of("severity", "INFO"),
                Instant.parse("2026-03-25T10:00:00Z"),
                LogSeverity.INFO,
                TimestampSource.SERVER,
                "corr-1"
        );
        CacheStatusResponse statusResponse = new CacheStatusResponse(
                CacheState.HEALTHY,
                Instant.parse("2026-03-25T10:01:00Z"),
                0,
                5
        );
        Mockito.when(logQueryService.fetchLogs(null, null))
                .thenReturn(new LogPageResponse(List.of(item), "cursor123", true, statusResponse));

        mockMvc.perform(get("/events").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("abc"))
                .andExpect(jsonPath("$.nextCursor").value("cursor123"))
                .andExpect(jsonPath("$.cacheStatus.state").value("HEALTHY"))
                .andExpect(jsonPath("$.dataComplete").value(true));
    }

    @Test
    void passesLimitAndCursorParamsToService() throws Exception {
        Mockito.when(logQueryService.fetchLogs(25, "abc"))
                .thenReturn(new LogPageResponse(List.of(), null, true, null));

        mockMvc.perform(get("/events")
                        .param("limit", "25")
                        .param("cursor", "abc"))
                .andExpect(status().isOk());

        verify(logQueryService).fetchLogs(eq(25), eq("abc"));
    }

    @Test
    void reusesCursorForSubsequentPages() throws Exception {
        Mockito.when(logQueryService.fetchLogs(null, null))
                .thenReturn(new LogPageResponse(List.of(), "cursor-1", true, null));
        Mockito.when(logQueryService.fetchLogs(null, "cursor-1"))
                .thenReturn(new LogPageResponse(List.of(), null, true, null));

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/events").param("cursor", "cursor-1"))
                .andExpect(status().isOk());

        verify(logQueryService).fetchLogs(null, "cursor-1");
    }
}
