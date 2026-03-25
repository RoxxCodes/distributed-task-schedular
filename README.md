# Distributed Task Scheduler

A Spring Boot application that executes one-time and recurring jobs at scale using Redis time buckets, PostgreSQL as the source of truth, and a Redis-backed execution queue.

## Architecture

```
Client → REST API → PostgreSQL (source of truth) + Redis Time Bucket
                                                          ↓
                                               TimeBucketPoller
                                                          ↓
                                              Redis Execution Queue
                                                          ↓
                                                   TaskWorker
                                                          ↓
                                           Execute + Update DB
                                           (recurring → reschedule)
```

## Prerequisites

- Java 17+
- Gradle 8.7+ (wrapper included)
- PostgreSQL 14+
- Redis 7+

## Setup

### 1. Create the database

```sql
CREATE DATABASE task_scheduler;
```

### 2. Configure connection

Edit `src/main/resources/application.yml` to match your PostgreSQL and Redis settings.

### 3. Build and run

```bash
./gradlew bootJar
java -jar build/libs/distributed-task-scheduler-0.0.1-SNAPSHOT.jar
```

Or run directly:

```bash
./gradlew bootRun
```

Flyway will automatically run the migration on startup.

## API Usage

### Create a one-time task

```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "send-welcome-email",
    "taskType": "ONE_TIME",
    "payload": "{\"userId\": 123, \"template\": \"welcome\"}",
    "executeAt": "2026-03-26T10:00:00Z"
  }'
```

### Create a recurring task

```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "generate-daily-report",
    "taskType": "RECURRING",
    "payload": "{\"reportType\": \"sales\"}",
    "cronExpression": "0 0 9 * * *"
  }'
```

> Cron format: `second minute hour day-of-month month day-of-week` (Spring 6-field cron)

### Get task status

```bash
curl http://localhost:8080/api/v1/tasks/{taskId}
```

### Cancel a task

```bash
curl -X DELETE http://localhost:8080/api/v1/tasks/{taskId}
```

## Key Design Decisions

| Concern | Decision |
|---|---|
| Source of truth | PostgreSQL — all task state persists here |
| Fast scheduling index | Redis Sorted Sets (time buckets) — ephemeral, rebuildable from DB |
| Execution queue | Redis List — swap to Kafka/RabbitMQ for durability |
| Distributed lock | Redis SETNX with TTL per bucket — prevents double-processing |
| Recurring tasks | Cron expression + compute-next-on-completion |
| Retries | Exponential backoff, re-insert into future time bucket |
| Failure recovery | RecurringTaskPromoter + stuck-task recovery scan |

## Scaling

Run multiple instances of the application. The distributed locking on time buckets ensures no task is processed more than once. Each instance runs both poller and worker threads.
