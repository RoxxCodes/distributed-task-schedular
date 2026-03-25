package com.scheduler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskQueueService {

    public static final String TOPIC = "task-execution";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void enqueue(String taskId) {
        kafkaTemplate.send(TOPIC, taskId, taskId)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[KAFKA_SEND_FAILED] taskId={} topic={}", taskId, TOPIC, ex);
                    } else {
                        log.debug("[KAFKA_SEND_OK] taskId={} topic={} partition={} offset={}",
                                taskId, TOPIC,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
