package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskStatusService {  
    // Сервис для обновления статусов задач  
    private final TaskUpdateService taskUpdateService;  

    // Метод выполнения задачи
    public void executeTask(Task task) {
        try {  
            // Имитация выполнения задачи  
            Thread.sleep(task.getDuration());  
            // Обновление статуса через отдельный сервис для корректной работы транзакций  
            taskUpdateService.updateTaskStatus(  
                task.getId(),   
                TaskStatus.COMPLETED,
                "Task completed successfully"  
            );  
        } catch (Exception e) {  
            // При ошибке обновляем статус на FAILED  
            taskUpdateService.updateTaskStatus(  
                task.getId(),   
                TaskStatus.FAILED,   
                "Error: " + e.getMessage()  
            );  
        }  
    }  
}  