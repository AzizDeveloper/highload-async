package dev.aziz.highloadasyncservice.repository;

import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

/*    // Метод для получения NEW задач с pessimistic lock и SKIP LOCKED
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "javax.persistence.lock.timeout", value = "0")
    }) //эта строчка сигнализирует Hibernate, что нужно использовать SKIP LOCKED. PostgreSQL добавляет SKIP LOCKED
    @Query("SELECT t FROM Task t WHERE t.status = 'NEW' ORDER BY t.id")
    Optional<Task> findAndLockFirstNewTask();*/

    @Query(value = """
        SELECT * FROM tasks
        WHERE status = 'NEW'
        ORDER BY id
        FOR UPDATE SKIP LOCKED
        LIMIT 1
        """, nativeQuery = true)
    Optional<Task> findAndLockFirstNewTask();

    // Обычный метод для других случаев
    Optional<Task> findFirstByStatus(TaskStatus status);
}