package com.scheduler.service;

import com.scheduler.model.dto.CreateTaskRequest;
import com.scheduler.model.dto.TaskResponse;
import com.scheduler.model.entity.Task;
import com.scheduler.model.enums.TaskStatus;
import com.scheduler.model.enums.TaskType;
import com.scheduler.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TimeBucketService timeBucketService;

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        validateRequest(request);

        Instant nextExecution = computeFirstExecution(request);
        String taskId = UUID.randomUUID().toString();

        Task task = Task.builder()
                .taskId(taskId)
                .taskName(request.getTaskName())
                .taskType(request.getTaskType())
                .payload(request.getPayload())
                .cronExpression(request.getCronExpression())
                .status(TaskStatus.SCHEDULED)
                .nextExecution(nextExecution)
                .maxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3)
                .retryCount(0)
                .build();

        task = taskRepository.save(task);

        timeBucketService.addTaskToBucket(taskId, nextExecution);

        log.info("[TASK_CREATED] taskId={} name={} type={} nextExecution={} maxRetries={}",
                taskId, task.getTaskName(), task.getTaskType(), nextExecution, task.getMaxRetries());

        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(String taskId) {
        Task task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + taskId));
        return toResponse(task);
    }

    @Transactional
    public void cancelTask(String taskId) {
        Task task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + taskId));

        if (task.getStatus() == TaskStatus.RUNNING) {
            throw new IllegalStateException("Cannot cancel a task that is currently running");
        }

        task.setStatus(TaskStatus.CANCELLED);
        taskRepository.save(task);

        long bucketKey = timeBucketService.computeBucketKey(task.getNextExecution());
        timeBucketService.removeFromBucket(bucketKey, taskId);

        log.info("[TASK_CANCELLED] taskId={} name={} previousStatus={}", taskId, task.getTaskName(), task.getStatus());
    }

    private void validateRequest(CreateTaskRequest request) {
        if (request.getTaskType() == TaskType.ONE_TIME) {
            if (request.getExecuteAt() == null) {
                throw new IllegalArgumentException("executeAt is required for ONE_TIME tasks");
            }
            if (request.getExecuteAt().isBefore(Instant.now())) {
                throw new IllegalArgumentException("executeAt must be in the future");
            }
        } else if (request.getTaskType() == TaskType.RECURRING) {
            if (request.getCronExpression() == null || request.getCronExpression().isBlank()) {
                throw new IllegalArgumentException("cronExpression is required for RECURRING tasks");
            }
            if (!CronExpression.isValidExpression(request.getCronExpression())) {
                throw new IllegalArgumentException("Invalid cron expression: " + request.getCronExpression());
            }
        }
    }

    private Instant computeFirstExecution(CreateTaskRequest request) {
        if (request.getTaskType() == TaskType.ONE_TIME) {
            return request.getExecuteAt();
        }
        CronExpression cron = CronExpression.parse(request.getCronExpression());
        LocalDateTime next = cron.next(LocalDateTime.now(ZoneOffset.UTC));
        if (next == null) {
            throw new IllegalArgumentException("Cron expression does not yield a future execution time");
        }
        return next.toInstant(ZoneOffset.UTC);
    }

    private TaskResponse toResponse(Task task) {
        return TaskResponse.builder()
                .taskId(task.getTaskId())
                .taskName(task.getTaskName())
                .taskType(task.getTaskType())
                .payload(task.getPayload())
                .cronExpression(task.getCronExpression())
                .status(task.getStatus())
                .nextExecution(task.getNextExecution())
                .lastExecution(task.getLastExecution())
                .maxRetries(task.getMaxRetries())
                .retryCount(task.getRetryCount())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    public static class TaskNotFoundException extends RuntimeException {
        public TaskNotFoundException(String message) {
            super(message);
        }
    }
}
