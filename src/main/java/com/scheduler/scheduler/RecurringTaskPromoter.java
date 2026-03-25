package com.scheduler.scheduler;

import com.scheduler.config.SchedulerProperties;
import com.scheduler.model.entity.Task;
import com.scheduler.model.enums.TaskStatus;
import com.scheduler.model.enums.TaskType;
import com.scheduler.repository.TaskRepository;
import com.scheduler.service.TimeBucketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Safety net that scans the DB for recurring tasks whose next execution
 * is within a near-future horizon and ensures they are present in a
 * Redis time bucket. Catches tasks missed due to worker crashes, Redis
 * evictions, or bucket TTL expiry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecurringTaskPromoter {

    private final TaskRepository taskRepository;
    private final TimeBucketService timeBucketService;
    private final SchedulerProperties properties;

    @Scheduled(fixedDelayString = "${scheduler.recurring-promote-interval-ms:60000}")
    public void promoteRecurringTasks() {
        Instant horizon = Instant.now().plusSeconds(properties.getPromoteHorizonSeconds());

        List<Task> tasks = taskRepository.findByTaskTypeAndStatusAndNextExecutionBefore(
                TaskType.RECURRING, TaskStatus.SCHEDULED, horizon);

        if (tasks.isEmpty()) {
            return;
        }

        log.info("[PROMOTER_SCAN] found={} horizon={}", tasks.size(), horizon);

        for (Task task : tasks) {
            timeBucketService.addTaskToBucket(task.getTaskId(), task.getNextExecution());
            log.debug("[PROMOTER_ADD] taskId={} name={} nextExecution={}", task.getTaskId(), task.getTaskName(), task.getNextExecution());
        }
    }

    /**
     * Recover tasks that got stuck in RUNNING state (e.g. worker crashed).
     * Resets them back to SCHEDULED so the poller can pick them up again.
     */
    @Transactional
    @Scheduled(fixedDelay = 120_000)
    public void recoverStuckTasks() {
        Instant threshold = Instant.now().minusSeconds(300); // stuck for > 5 minutes
        List<Task> stuckTasks = taskRepository.findStuckRunningTasks(threshold);

        if (!stuckTasks.isEmpty()) {
            log.warn("[RECOVERY_SCAN] stuckCount={} threshold={}", stuckTasks.size(), threshold);
        }

        for (Task task : stuckTasks) {
            task.setRetryCount(task.getRetryCount() + 1);

            if (task.getRetryCount() >= task.getMaxRetries()) {
                task.setStatus(TaskStatus.FAILED);
                log.error("[RECOVERY_FAILED] taskId={} name={} retries={} reason=max_retries_exceeded",
                        task.getTaskId(), task.getTaskName(), task.getRetryCount());
            } else {
                task.setStatus(TaskStatus.SCHEDULED);
                timeBucketService.addTaskToBucket(task.getTaskId(), Instant.now());
                log.warn("[RECOVERY_RESCHEDULED] taskId={} name={} attempt={}/{} stuckSince={}",
                        task.getTaskId(), task.getTaskName(), task.getRetryCount(), task.getMaxRetries(), task.getUpdatedAt());
            }
            taskRepository.save(task);
        }
    }
}
