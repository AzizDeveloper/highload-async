package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.TaskRun;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskStatusService {

    private final TaskUpdateService taskUpdateService;

    public void executeTask(TaskRun run) {
        long elapsed = 0;
        int progress = 0;

        try {
            while (elapsed < run.duration()) {
                long step = Math.min(1000L, run.duration() - elapsed);
                Thread.sleep(step);
                elapsed += step;
                progress = Math.min(
                        99, (int) ((double) elapsed / run.duration() * 100)
                );

                if (elapsed < run.duration()
                        && !taskUpdateService.updateProgress(
                        run.id(), run.executionId(), progress
                )) {
                    log.warn("Попытка {} задачи {} больше не активна",
                            run.executionId(), run.id());
                    return;
                }
            }
        } catch (InterruptedException e) {
            log.info("Выполнение задачи {} прервано", run.id());
            try {
                saveFinal(run, TaskStatus.FAILED, progress, "Выполнение прервано");
            } finally {
                Thread.currentThread().interrupt();
            }
            return;
        } catch (DataAccessException | TransactionException e) {
            // Ошибка сохранения состояния не равна ошибке бизнес-операции
            log.error("Не удалось сохранить промежуточное состояние задачи {}",
                    run.id(), e);
            return;
        } catch (RuntimeException e) {
            log.error("Ошибка выполнения задачи {}", run.id(), e);
            saveFinal(run, TaskStatus.FAILED, progress, "Ошибка выполнения задачи");
            return;
        }

        // Сохранение результата отдельно от блока выполнения
        saveFinal(run, TaskStatus.COMPLETED, 100, "Задача выполнена успешно");
    }

    private void saveFinal(
            TaskRun run, TaskStatus status, int progress, String result
    ) {
        try {
            boolean updated = taskUpdateService.updateTaskStatus(
                    run.id(), run.executionId(), status, progress, result
            );
            if (!updated) {
                log.warn("Финальный результат старой попытки {} задачи {} отклонен",
                        run.executionId(), run.id());
            }
        } catch (RuntimeException e) {
            log.error("Не удалось сохранить финальное состояние задачи {}",
                    run.id(), e);
        }
    }

}