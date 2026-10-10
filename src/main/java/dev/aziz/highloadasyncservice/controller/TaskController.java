package dev.aziz.highloadasyncservice.controller;

import dev.aziz.highloadasyncservice.dto.ErrorResponse;
import dev.aziz.highloadasyncservice.dto.TaskAcceptedResponse;
import dev.aziz.highloadasyncservice.dto.TaskRequestDTO;
import dev.aziz.highloadasyncservice.dto.TaskResponseDTO;
import dev.aziz.highloadasyncservice.service.TaskPublisher;
import dev.aziz.highloadasyncservice.service.TaskQueryService;
import dev.aziz.highloadasyncservice.service.TaskRetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Validated
@Tag(name = "Task API", description = "API для получения состояния задач")
public class TaskController {
    private final TaskQueryService taskQueryService;
    private final TaskPublisher taskPublisher;
    private final TaskRetryService taskRetryService;

    @GetMapping("/{id}")
    @Operation(summary = "Получить статус и результат задачи")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Задача найдена",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TaskResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Некорректный идентификатор",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Задача не найдена",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "База данных недоступна",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервиса",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponseDTO> getTask(
            @PathVariable("id") @Positive Long id
    ) {
        return ResponseEntity.ok(taskQueryService.findById(id));
    }

    @GetMapping("/{eventId}/events")
    @Operation(summary = "Получить статус и результат задачи")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Задача найдена",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TaskResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Некорректный идентификатор",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Задача не найдена",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "База данных недоступна",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервиса",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponseDTO> getTaskByEventId(
            @PathVariable("eventId") UUID eventId
    ) {
        return ResponseEntity.ok(taskQueryService.findByEventId(eventId));
    }

    @PostMapping
    public ResponseEntity<?> saveTask(@RequestBody TaskRequestDTO taskRequestDTO) {
        String eventId = taskPublisher.publish(taskRequestDTO);
        return ResponseEntity.accepted().body(new TaskAcceptedResponse(eventId));
    }

    @PostMapping("/admin/retry/{id}")
    public ResponseEntity<?> retryTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskRetryService.retryFailedTask(id));
    }

}