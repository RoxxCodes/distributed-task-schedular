package com.scheduler.worker;

import com.scheduler.model.entity.Task;
import com.scheduler.model.enums.TaskStatus;
import com.scheduler.model.enums.TaskType;
import com.scheduler.repository.TaskRepository;
import com.scheduler.service.TaskExecutionService;
import com.scheduler.service.TaskQueueService;
import com.scheduler.service.TimeBucketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Consumes tasks from the Kafka execution topic and runs them.
 * On completion of recurring tasks, computes the next fire time
 * and re-inserts into the appropriate time bucket.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskWorker {

    private final TaskExecutionService executionService;
    private final TaskRepository taskRepository;
    private final TimeBucketService timeBucketService;

    @KafkaListener(topics = TaskQueueService.TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String taskId = record.value();
        log.info("[WORKER_RECEIVED] taskId={} partition={} offset={}",
                taskId, record.partition(), record.offset());

        try {
            processTask(taskId);
        } catch (Exception e) {
            log.error("[WORKER_UNEXPECTED_ERROR] taskId={} error={}", taskId, e.getMessage(), e);
        } finally {
            ack.acknowledge();
        }
    }

    private void processTask(String taskId) {
        Optional<Task> optionalTask = taskRepository.findByTaskId(taskId);
        if (optionalTask.isEmpty()) {
            log.warn("[WORKER_SKIP] taskId={} reason=not_found_in_db", taskId);
            return;
        }

        Task task = optionalTask.get();

        if (task.getStatus() != TaskStatus.QUEUED) {
            log.warn("[WORKER_SKIP] taskId={} reason=unexpected_state currentStatus={}", taskId, task.getStatus());
            return;
        }

        log.info("[TASK_STATUS_CHANGE] taskId={} from=QUEUED to=RUNNING", taskId);
        task.setStatus(TaskStatus.RUNNING);
        taskRepository.save(task);

        try {
            executionService.execute(task);
            onSuccess(task);
        } catch (Exception e) {
            log.error("[TASK_EXEC_FAILED] taskId={} name={} error={}", taskId, task.getTaskName(), e.getMessage(), e);
            onFailure(task);
        }
    }

    private void onSuccess(Task task) {
        task.setLastExecution(Instant.now());

        if (task.getTaskType() == TaskType.RECURRING) {
            Instant nextTime = computeNextExecution(task.getCronExpression());
            task.setNextExecution(nextTime);
            task.setStatus(TaskStatus.SCHEDULED);
            task.setRetryCount(0);
            taskRepository.save(task);
            timeBucketService.addTaskToBucket(task.getTaskId(), nextTime);
            log.info("[TASK_RESCHEDULED] taskId={} name={} nextExecution={}", task.getTaskId(), task.getTaskName(), nextTime);
        } else {
            task.setStatus(TaskStatus.COMPLETED);
            taskRepository.save(task);
            log.info("[TASK_COMPLETED] taskId={} name={}", task.getTaskId(), task.getTaskName());
        }
    }

    private void onFailure(Task task) {
        task.setRetryCount(task.getRetryCount() + 1);
        task.setLastExecution(Instant.now());

        if (task.getRetryCount() >= task.getMaxRetries()) {
            task.setStatus(TaskStatus.FAILED);
            taskRepository.save(task);
            log.error("[TASK_PERMANENTLY_FAILED] taskId={} name={} totalAttempts={}",
                    task.getTaskId(), task.getTaskName(), task.getRetryCount());
        } else {
            long backoffSeconds = (long) Math.pow(2, task.getRetryCount()) * 10;
            Instant retryAt = Instant.now().plusSeconds(backoffSeconds);
            task.setNextExecution(retryAt);
            task.setStatus(TaskStatus.SCHEDULED);
            taskRepository.save(task);
            timeBucketService.addTaskToBucket(task.getTaskId(), retryAt);
            log.warn("[TASK_RETRY_SCHEDULED] taskId={} name={} attempt={}/{} retryAt={} backoffSec={}",
                    task.getTaskId(), task.getTaskName(), task.getRetryCount(), task.getMaxRetries(), retryAt, backoffSeconds);
        }
    }

    private Instant computeNextExecution(String cronExpression) {
        CronExpression cron = CronExpression.parse(cronExpression);
        LocalDateTime next = cron.next(LocalDateTime.now(ZoneOffset.UTC));
        if (next == null) {
            throw new IllegalStateException("Cron expression yielded no next execution time");
        }
        return next.toInstant(ZoneOffset.UTC);
    }
}
