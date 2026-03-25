CREATE TABLE tasks (
    id              BIGSERIAL PRIMARY KEY,
    task_id         VARCHAR(64)  NOT NULL UNIQUE,
    task_name       VARCHAR(255) NOT NULL,
    task_type       VARCHAR(20)  NOT NULL,
    payload         TEXT,
    cron_expression VARCHAR(100),
    status          VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    next_execution  TIMESTAMP WITH TIME ZONE NOT NULL,
    last_execution  TIMESTAMP WITH TIME ZONE,
    max_retries     INT NOT NULL DEFAULT 3,
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tasks_task_id ON tasks(task_id);
CREATE INDEX idx_tasks_status_next_exec ON tasks(status, next_execution);
CREATE INDEX idx_tasks_type_status_next_exec ON tasks(task_type, status, next_execution);
