package com.scheduler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "scheduler")
public class SchedulerProperties {

    private long bucketSizeSeconds = 60;
    private long pollIntervalMs = 5000;
    private int workerThreadCount = 10;
    private long recurringPromoteIntervalMs = 60000;
    private long promoteHorizonSeconds = 120;
}
