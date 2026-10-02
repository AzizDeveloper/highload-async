CREATE TABLE tasks
(
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255),
    duration        BIGINT,
    status          VARCHAR(50) NOT NULL,
    result          TEXT,
    version         BIGINT NOT NULL DEFAULT 0,
    idempotency_key UUID NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at      TIMESTAMP WITH TIME ZONE,
    retry_count     BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_tasks_idempotency_key UNIQUE (idempotency_key)
);

-- Index for the Worker polling (findFirstByStatus)
CREATE INDEX idx_tasks_status ON tasks (status);

-- Index for the Stuck Task Scheduler (status + started_at)
CREATE INDEX idx_tasks_status_started_at ON tasks (status, started_at);