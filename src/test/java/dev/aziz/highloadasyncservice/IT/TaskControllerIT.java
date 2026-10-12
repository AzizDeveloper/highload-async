package dev.aziz.highloadasyncservice.IT;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aziz.highloadasyncservice.dto.TaskRequestDTO;
import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import dev.aziz.highloadasyncservice.service.TaskPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TaskControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    // If you do not want to test real publishing, mock TaskPublisher.
    // If you want to test real Kafka/RabbitMQ publishing, remove this and use a broker container.
    @MockitoBean
    private TaskPublisher taskPublisher;

    @Test
    void getTask_whenTaskExists_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();
        Task task = new Task();
        task.setEventId(eventId);
        task.setName("testing task");
        task.setDuration(3500L);
        Task savedTask = taskRepository.save(task);

        mockMvc.perform(
                        get("/api/tasks/{id}", savedTask.getId())
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedTask.getId()));
    }

    @Test
    void getTask_whenTaskDoesNotExist_returns404() throws Exception {
        mockMvc.perform(
                        get("/api/tasks/{id}", 999999L)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isNotFound());
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
    void getTaskByEventId_whenTaskExists_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();
        Task task = new Task();
        task.setEventId(eventId);
        task.setName("testing task");
        task.setDuration(3500L);
        Task savedTask = taskRepository.save(task);

        mockMvc.perform(
                        get("/api/tasks/{eventId}/events", eventId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()));
    }

    @Test
    void getTaskByEventId_whenTaskDoesNotExist_returns404() throws Exception {
        UUID unknownEventId = UUID.randomUUID();

        mockMvc.perform(
                        get("/api/tasks/{eventId}/events", unknownEventId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void saveTask_whenRequestIsValid_returns202() throws Exception {
        UUID eventId = UUID.randomUUID();

        when(taskPublisher.publish(any(TaskRequestDTO.class)))
                .thenReturn(eventId.toString());

        // Adjust constructor according to your real TaskRequestDTO
        TaskRequestDTO request = new TaskRequestDTO();
        request.setEventId(eventId);
        request.setName("testing task");
        request.setDuration(2500L);

        mockMvc.perform(
                        post("/api/tasks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()));

        verify(taskPublisher).publish(any(TaskRequestDTO.class));
    }

    @Test
    void retryTask_whenFailedTaskExists_returns200() throws Exception {
        Task task = new Task();
        task.setEventId(UUID.randomUUID());
        task.setName("testing task");
        task.setDuration(3500L);
        Task failedTask = taskRepository.save(task);

        mockMvc.perform(
                        post("/api/tasks/admin/retry/{id}", failedTask.getId())
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk());
    }

}