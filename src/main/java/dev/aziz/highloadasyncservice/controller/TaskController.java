package dev.aziz.highloadasyncservice.controller;

import dev.aziz.highloadasyncservice.dto.TaskResponseDTO;
import dev.aziz.highloadasyncservice.exception.TaskNotFoundException;
import dev.aziz.highloadasyncservice.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Task API", description = "API для работы с задачами")
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/{id}")
    @Operation(summary = "Получить статус задачи")
    public ResponseEntity<TaskResponseDTO> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.findById(id));
    }
}