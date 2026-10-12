package dev.aziz.highloadasyncservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aziz.highloadasyncservice.dto.TaskRequestDTO;
import dev.aziz.highloadasyncservice.dto.TaskResponseDTO;
import dev.aziz.highloadasyncservice.exception.GlobalExceptionHandler;
import dev.aziz.highloadasyncservice.exception.TaskNotFoundException;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import dev.aziz.highloadasyncservice.service.TaskPublisher;
import dev.aziz.highloadasyncservice.service.TaskQueryService;
import dev.aziz.highloadasyncservice.service.TaskRetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.naming.ServiceUnavailableException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(GlobalExceptionHandler.class)
class TaskControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskQueryService taskQueryService;

    @MockitoBean
    private TaskPublisher taskPublisher;

    @MockitoBean
    private TaskRetryService taskRetryService;

    @Test
    void getTask_whenTaskExists_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();

        // Adjust constructor according to your real TaskResponseDTO
        TaskResponseDTO response = new TaskResponseDTO(
                1L,
                eventId,
                "COMPLETED",
                4000L,
                TaskStatus.NEW,
                0,
                "Result",
                null,
                null,
                null
        );

        when(taskQueryService.findById(1L)).thenReturn(response);

        mockMvc.perform(
                        get("/api/tasks/{id}", 1L)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getTask_whenIdIsZero_returns400() throws Exception {
        mockMvc.perform(
                        get("/api/tasks/{id}", 0L)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTask_whenTaskNotFound_returns404() throws Exception {
        when(taskQueryService.findById(999L))
                .thenThrow(new TaskNotFoundException(999L));

        mockMvc.perform(
                        get("/api/tasks/{id}", 999L)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void getTaskByEventId_whenEventIdIsInvalid_returns400() throws Exception {
        mockMvc.perform(
                        get("/api/tasks/{eventId}/events", "not-a-uuid")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTaskByEventId_whenTaskNotFound_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();

        when(taskQueryService.findByEventId(eventId))
                .thenThrow(new TaskNotFoundException(eventId));

        mockMvc.perform(
                        get("/api/tasks/{eventId}/events", eventId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void getTask_whenDatabaseUnavailable_returns503() throws Exception {
        when(taskQueryService.findById(1L))
                .thenThrow(new ServiceUnavailableException("Database unavailable"));

        mockMvc.perform(
                        get("/api/tasks/{id}", 1L)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void getTask_whenUnexpectedError_returns500() throws Exception {
        when(taskQueryService.findById(1L))
                .thenThrow(new IllegalStateException("Unexpected error"));

        mockMvc.perform(
                        get("/api/tasks/{id}", 1L)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void saveTask_whenRequestIsValid_returns202() throws Exception {
        UUID eventId = UUID.randomUUID();

        when(taskPublisher.publish(any(TaskRequestDTO.class)))
                .thenReturn(eventId.toString());

        // Adjust constructor according to your real TaskRequestDTO
        TaskRequestDTO request = new TaskRequestDTO();
        request.setName("payload");
        request.setEventId(eventId);
        request.setDuration(2000L);

        mockMvc.perform(
                        post("/api/tasks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(eventId));

        verify(taskPublisher).publish(any(TaskRequestDTO.class));
    }

    @Test
    void retryTask_whenServiceReturnsResult_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();

        // Adjust constructor according to your real TaskResponseDTO
        TaskResponseDTO response = new TaskResponseDTO(
                1L,
                eventId,
                "RETRYING",
                4000L,
                TaskStatus.NEW,
                0,
                "Result",
                null,
                null,
                null
        );

        when(taskRetryService.retryFailedTask(1L)).thenReturn(true);

        mockMvc.perform(
                        post("/api/tasks/admin/retry/{id}", 1L)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RETRYING"));
    }

}