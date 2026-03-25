package com.scheduler.service;

import com.scheduler.model.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Pluggable execution engine. Replace the body of {@link #execute(Task)}
 * with real logic — HTTP calls, message publishing, script execution, etc.
 */
@Slf4j
@Service
public class TaskExecutionService {

    public void execute(Task task) {
        log.info("[TASK_EXEC_START] taskId={} name={} type={}", task.getTaskId(), task.getTaskName(), task.getTaskType());
        log.debug("[TASK_EXEC_START] taskId={} payload={}", task.getTaskId(), task.getPayload());

        long startMs = System.currentTimeMillis();
        simulateExecution(task);
        long durationMs = System.currentTimeMillis() - startMs;

        log.info("[TASK_EXEC_DONE] taskId={} name={} durationMs={}", task.getTaskId(), task.getTaskName(), durationMs);
    }

    private void simulateExecution(Task task) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task execution interrupted", e);
        }
    }
}
