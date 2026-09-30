package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.exception.TaskNotFoundException;
import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskUpdateService {  

    private final TaskRepository taskRepository;

    // Метод обновления статуса задачи в новой транзакции  
    // REQUIRES_NEW гарантирует создание новой транзакции  
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateTaskStatus(Long taskId, TaskStatus newStatus, String result) {
        // Получаем актуальное состояние задачи  
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new TaskNotFoundException(taskId));
        
        // Логируем информацию об обновлении  
        log.debug("Updating task {} to status {}. Current version: {}",   
                 taskId, newStatus, task.getVersion());  
        
        // Обновляем статус и результат  
        task.setStatus(newStatus);  
        task.setResult(result);  
        // Сохраняем изменения в БД  
        taskRepository.save(task);  
    }

}