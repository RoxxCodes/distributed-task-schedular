package com.scheduler.service;

import com.scheduler.config.SchedulerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeBucketService {

    private static final String BUCKET_KEY_PREFIX = "task_bucket:";

    private final StringRedisTemplate redisTemplate;
    private final SchedulerProperties properties;

    public void addTaskToBucket(String taskId, Instant executionTime) {
        long bucketKey = computeBucketKey(executionTime);
        String key = BUCKET_KEY_PREFIX + bucketKey;

        redisTemplate.opsForZSet().add(key, taskId, executionTime.toEpochMilli());
        redisTemplate.expire(key, Duration.ofSeconds(properties.getBucketSizeSeconds() * 10));

        log.debug("[BUCKET_ADD] taskId={} bucket={} executionTime={}", taskId, bucketKey, executionTime);
    }

    public Set<String> getTasksDueBefore(long bucketKey, Instant now) {
        String key = BUCKET_KEY_PREFIX + bucketKey;
        Set<String> taskIds = redisTemplate.opsForZSet().rangeByScore(key, 0, now.toEpochMilli());
        return taskIds != null ? taskIds : Collections.emptySet();
    }

    public void removeFromBucket(long bucketKey, String taskId) {
        String key = BUCKET_KEY_PREFIX + bucketKey;
        redisTemplate.opsForZSet().remove(key, taskId);
    }

    public long computeBucketKey(Instant time) {
        long epochSecond = time.getEpochSecond();
        return epochSecond - (epochSecond % properties.getBucketSizeSeconds());
    }

    public long getCurrentBucketKey() {
        return computeBucketKey(Instant.now());
    }

    public long getPreviousBucketKey() {
        return getCurrentBucketKey() - properties.getBucketSizeSeconds();
    }
}
