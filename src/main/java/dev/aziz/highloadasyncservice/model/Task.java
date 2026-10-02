package dev.aziz.highloadasyncservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Data
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private Long duration;
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    private String result;
    @Version
    private Long version;
    @Column(nullable = false, updatable = false)
    private UUID idempotencyKey;
    @Column(nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
    private Instant startedAt;
    private long retryCount;

}