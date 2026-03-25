package com.scheduler.model.dto;

import com.scheduler.model.enums.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTaskRequest {

    @NotBlank(message = "Task name is required")
    private String taskName;

    @NotNull(message = "Task type is required")
    private TaskType taskType;

    private String payload;

    /** Required for RECURRING tasks (Spring cron format, 6 fields) */
    private String cronExpression;

    /** Required for ONE_TIME tasks */
    private Instant executeAt;

    private Integer maxRetries;
}
