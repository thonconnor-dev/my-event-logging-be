package com.example.eventlog.controller;

import com.example.eventlog.repository.EventRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerIntegrationTest {

    private static final Path TEMP_DIR;

    static {
        try {
            TEMP_DIR = Files.createTempDirectory("eventlog-sqlite");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void configureDb(DynamicPropertyRegistry registry) {
        registry.add("EVENT_LOG_DB_PATH", () -> TEMP_DIR.resolve("event-log.db").toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private EventRecordRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @AfterEach
    void resetSpies() {
        Mockito.reset(repository);
    }

    @Test
    void storesEventInDatabase() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "callerId": "integration-client",
                                  "message": "user login"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.eventId").isNotEmpty());

        assertThat(repository.findAll())
                .hasSize(1)
                .first()
                .matches(record -> record.getCallerId().equals("integration-client"));
    }

    @Test
    void dedupReturnsExistingEventId() throws Exception {
        String payload = """
                {
                  "callerId": "integration-client",
                  "message": "duplicate check",
                  "timestamp": "2026-03-01T00:00:00Z"
                }
                """;

        String firstId = mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstEventId = objectMapper.readTree(firstId).get("eventId").asText();

        String secondResponse = mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secondEventId = objectMapper.readTree(secondResponse).get("eventId").asText();

        assertThat(secondEventId).isEqualTo(firstEventId);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void returnsServerErrorWhenDatabaseUnavailable() throws Exception {
        Mockito.doThrow(new DataAccessResourceFailureException("db down"))
                .when(repository).saveAndFlush(any());

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "callerId": "integration-client",
                                  "message": "should fail"
                                }
                                """))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void getEventsReadsFromDatabaseWithPaging() throws Exception {
        postEvent("""
                {
                  "callerId": "integration-client",
                  "message": "first event",
                  "timestamp": "2026-03-01T00:00:00Z"
                }
                """);
        postEvent("""
                {
                  "callerId": "integration-client",
                  "message": "second event",
                  "timestamp": "2026-03-02T00:00:00Z"
                }
                """);

        String firstPage = mockMvc.perform(get("/events")
                        .param("pageSize", "1")
                        .param("from", "2026-03-01T00:00:00Z")
                        .param("to", "2026-03-03T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].message").value("second event"))
                .andExpect(jsonPath("$.nextPageToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String nextToken = objectMapper.readTree(firstPage).get("nextPageToken").asText();

        mockMvc.perform(get("/events")
                        .param("pageToken", nextToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].message").value("first event"))
                .andExpect(jsonPath("$.nextPageToken").doesNotExist());
    }

    private void postEvent(String payload) throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }
}
