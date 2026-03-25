package com.scheduler.model.dto;

import com.scheduler.model.enums.TaskStatus;
import com.scheduler.model.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskResponse {

    private String taskId;
    private String taskName;
    private TaskType taskType;
    private String payload;
    private String cronExpression;
    private TaskStatus status;
    private Instant nextExecution;
    private Instant lastExecution;
    private int maxRetries;
    private int retryCount;
    private Instant createdAt;
    private Instant updatedAt;
}
