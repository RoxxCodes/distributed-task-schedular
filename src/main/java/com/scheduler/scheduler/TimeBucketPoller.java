package com.scheduler.scheduler;

import com.scheduler.config.SchedulerProperties;
import com.scheduler.model.enums.TaskStatus;
import com.scheduler.repository.TaskRepository;
import com.scheduler.service.TaskQueueService;
import com.scheduler.service.TimeBucketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Polls Redis time buckets for tasks that are due, then pushes them
 * onto the execution queue. Uses distributed locking per bucket to
 * prevent double-processing across multiple instances.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeBucketPoller {

    private final TimeBucketService timeBucketService;
    private final TaskQueueService taskQueueService;
    private final TaskRepository taskRepository;
    private final StringRedisTemplate redisTemplate;
    private final SchedulerProperties properties;

    @Scheduled(fixedDelayString = "${scheduler.poll-interval-ms:5000}")
    public void pollBuckets() {
        Instant now = Instant.now();
        long currentBucket = timeBucketService.getCurrentBucketKey();
        long previousBucket = timeBucketService.getPreviousBucketKey();

        for (long bucket : List.of(previousBucket, currentBucket)) {
            processTimeBucket(bucket, now);
        }
    }

    private void processTimeBucket(long bucketKey, Instant now) {
        String lockKey = "lock:bucket:" + bucketKey;

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", Duration.ofSeconds(30));

        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }

        try {
            Set<String> taskIds = timeBucketService.getTasksDueBefore(bucketKey, now);
            if (taskIds.isEmpty()) {
                return;
            }

            log.info("[POLLER_BUCKET] bucket={} dueCount={}", bucketKey, taskIds.size());

            for (String taskId : taskIds) {
                enqueueTask(bucketKey, taskId);
            }
        } catch (Exception e) {
            log.error("[POLLER_ERROR] bucket={} error={}", bucketKey, e.getMessage(), e);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private void enqueueTask(long bucketKey, String taskId) {
        try {
            int updated = taskRepository.compareAndSetStatus(
                    taskId, TaskStatus.SCHEDULED, TaskStatus.QUEUED, Instant.now());

            if (updated > 0) {
                taskQueueService.enqueue(taskId);
                log.info("[POLLER_ENQUEUE] taskId={} bucket={} status=SCHEDULED->QUEUED", taskId, bucketKey);
            } else {
                log.debug("[POLLER_SKIP] taskId={} bucket={} reason=not_in_scheduled_state", taskId, bucketKey);
            }

            timeBucketService.removeFromBucket(bucketKey, taskId);
        } catch (Exception e) {
            log.error("[POLLER_ENQUEUE_FAILED] taskId={} bucket={} error={}", taskId, bucketKey, e.getMessage(), e);
        }
    }
}
