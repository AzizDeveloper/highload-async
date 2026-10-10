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
            SELECT *
            FROM tasks
            WHERE status = 'NEW'
              AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP)
              AND attempts < max_attempts
            ORDER BY id
            LIMIT 1
            FOR UPDATE SKIP LOCKED;
            """, nativeQuery = true)
    Optional<Task> findAndLockFirstNewTask();

    @Modifying(clearAutomatically = true)
    @Query(value = """
            WITH expired AS (
                SELECT id
                FROM tasks
                WHERE status = 'IN_PROGRESS'
                  AND lease_until < CURRENT_TIMESTAMP
                ORDER BY lease_until, id
                LIMIT 100
                FOR UPDATE SKIP LOCKED
            )
            UPDATE tasks t
            SET status = CASE
                    WHEN t.attempts < t.max_attempts THEN 'NEW'
                    ELSE 'FAILED'
                END,
                execution_id = NULL,
                lease_until = NULL,
                next_retry_at = CASE
                    WHEN t.attempts < t.max_attempts
                        THEN CURRENT_TIMESTAMP + INTERVAL '30 seconds'
                    ELSE NULL
                END,
                started_at = CASE
                    WHEN t.attempts < t.max_attempts THEN NULL
                    ELSE t.started_at
                END,
                finished_at = CASE
                    WHEN t.attempts >= t.max_attempts THEN CURRENT_TIMESTAMP
                    ELSE NULL
                END,
                progress = CASE
                    WHEN t.attempts < t.max_attempts THEN 0
                    ELSE t.progress
                END,
                result = 'Истекло время владения задачей',
                version = t.version + 1
            FROM expired e
            WHERE t.id = e.id
            """, nativeQuery = true)
    int recoverStuckTasks();

    @Modifying(clearAutomatically = true)
    @Query(value = """
            WITH retryable AS (
                SELECT id
                FROM tasks
                WHERE status = 'FAILED'
                  AND next_retry_at IS NOT NULL
                  AND next_retry_at <= CURRENT_TIMESTAMP
                  AND attempts < max_attempts
                ORDER BY next_retry_at, id
                LIMIT 100
                FOR UPDATE SKIP LOCKED
            )
            UPDATE tasks t
            SET status = 'NEW',
                execution_id = NULL,
                lease_until = NULL,
                next_retry_at = NULL,
                started_at = NULL,
                finished_at = NULL,
                progress = 0,
                result = NULL,
                version = t.version + 1
            FROM retryable r
            WHERE t.id = r.id
            """, nativeQuery = true)
    int retryEligibleFailedTasks();

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