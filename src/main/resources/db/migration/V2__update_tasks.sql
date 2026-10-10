ALTER TABLE tasks
    ADD COLUMN attempts       INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN max_attempts   INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN lease_until    TIMESTAMPTZ,
    ADD COLUMN next_retry_at  TIMESTAMPTZ
;

ALTER TABLE tasks
    ADD CONSTRAINT chk_tasks_attempts_non_negative
        CHECK (attempts >= 0),
    ADD CONSTRAINT chk_tasks_max_attempts_positive
        CHECK (max_attempts > 0),
    ADD CONSTRAINT chk_tasks_attempts_within_limit
        CHECK (attempts <= max_attempts);

CREATE INDEX IF NOT EXISTS idx_tasks_in_progress_lease_until
    ON tasks (lease_until, id)
    WHERE status = 'IN_PROGRESS';

CREATE INDEX IF NOT EXISTS idx_tasks_new_claim
    ON tasks (id)
    WHERE status = 'NEW';

CREATE INDEX IF NOT EXISTS idx_tasks_failed_retry_due
    ON tasks (next_retry_at, id)
    WHERE status = 'FAILED'
      AND next_retry_at IS NOT NULL
      AND attempts < max_attempts;