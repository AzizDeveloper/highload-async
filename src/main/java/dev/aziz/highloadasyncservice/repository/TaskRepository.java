package dev.aziz.highloadasyncservice.repository;

import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query(value = """
    SELECT * FROM tasks
    WHERE status = 'NEW'
    ORDER BY id
    LIMIT 1
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    Optional<Task> findAndLockFirstNewTask();

    Optional<Task> findFirstByStatusOrderByIdAsc(TaskStatus status);

    Optional<Task> findByEventId(UUID eventId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO tasks (event_id, name, duration)
            VALUES (:eventId, :name, :duration)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("name") String name,
            @Param("duration") long duration
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Task t
            SET t.progress = :progress, t.version = t.version + 1
            WHERE t.id = :id
              AND t.executionId = :executionId
              AND t.status = :runningStatus
              AND t.progress <= :progress
            """)
    int updateProgress(
            @Param("id") Long id,
            @Param("executionId") UUID executionId,
            @Param("runningStatus") TaskStatus runningStatus,
            @Param("progress") int progress
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Task t
            SET t.status = :newStatus,
                t.progress = :progress,
                t.result = :result,
                t.finishedAt = :finishedAt,
                t.version = t.version + 1
            WHERE t.id = :id
              AND t.executionId = :executionId
              AND t.status = :runningStatus
            """)
    int finishExecution(
            @Param("id") Long id,
            @Param("executionId") UUID executionId,
            @Param("runningStatus") TaskStatus runningStatus,
            @Param("newStatus") TaskStatus newStatus,
            @Param("progress") int progress,
            @Param("result") String result,
            @Param("finishedAt") Instant finishedAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Task t WHERE t.id = :id")
    Optional<Task> findByIdForUpdate(@Param("id") Long id);

}