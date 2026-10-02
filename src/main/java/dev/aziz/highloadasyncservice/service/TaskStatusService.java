package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.exception.TaskNotFoundException;
import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskStatusService {

    private final TaskRepository taskRepository;

    // Метод обновления статуса задачи в отдельной транзакции
    @Transactional
    public void updateTaskStatus(Long taskId, TaskStatus status, String result) {
        try {
            // Получаем задачу из БД
            Task task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new TaskNotFoundException(taskId));

            // Логируем информацию об обновлении
            log.debug("Updating task {} to status {}. Current version: {}",
                    taskId, status, task.getVersion());

            // Обновляем статус и результат
            task.setStatus(status);
            task.setResult(result);
            taskRepository.save(task);
        } catch (Exception e) {
            // Логируем ошибку и пробрасываем исключение выше
            log.error("Error updating task status: {}", e.getMessage(), e);
            throw e;
        }
    }

}