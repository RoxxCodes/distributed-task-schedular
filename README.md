# Distributed Task Scheduler

A Spring Boot application that executes one-time and recurring jobs at scale using Redis time buckets, PostgreSQL as the source of truth, and a Kafka-backed execution queue.

## Architecture

```
Client → REST API → PostgreSQL (source of truth) + Redis Time Bucket
                                                          ↓
                                               TimeBucketPoller
                                                          ↓
                                              Kafka (task-execution topic)
                                                          ↓
                                                TaskWorker (@KafkaListener)
                                                          ↓
                                           Execute + Update DB
                                           (recurring → reschedule)
```

## Prerequisites

- Java 17+
- Docker & Docker Compose
- Gradle 8.7+ (wrapper included)

## Quick Start

### 1. Start infrastructure

```bash
docker-compose up -d
```

This starts **PostgreSQL** (port 5433), **Redis** (port 6379), and **Kafka** (port 9092). The `task_scheduler` database is created automatically.

### 2. Run the application

```bash
./gradlew bootRun
```

Flyway runs the database migration on first startup. Kafka auto-creates the `task-execution` topic on first message.

### 3. Verify everything is running

```bash
# Check containers
docker-compose ps

# Check app health
curl http://localhost:8080/api/v1/tasks/non-existent-id
# Should return 404 JSON — means the app is up
```

## Stopping & Restarting

```bash
# Stop the Spring Boot app
# (Ctrl+C if running in foreground, or kill the process)

# Stop infrastructure
docker-compose down

# Stop infrastructure AND delete data volumes
docker-compose down -v
```

## Building a JAR

```bash
./gradlew bootJar
java -jar build/libs/distributed-task-scheduler-0.0.1-SNAPSHOT.jar
```

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

### Supported `executeAt` formats

| Format | Example |
|---|---|
| ISO UTC | `"2026-03-26T10:00:00Z"` |
| ISO UTC with millis | `"2026-03-26T10:00:00.000Z"` |
| ISO with offset | `"2026-03-26T00:39:00+05:30"` |
| Space-separated with offset | `"2026-03-26 00:39:00.000 +0530"` |
| Local datetime (treated as UTC) | `"2026-03-26 00:39:00"` |
| Epoch millis | `"1774656600000"` |

### Cron format

Spring 6-field cron: `second minute hour day-of-month month day-of-week`

| Expression | Meaning |
|---|---|
| `0 * * * * *` | Every minute |
| `0 0 * * * *` | Every hour |
| `0 0 9 * * *` | Daily at 9 AM UTC |
| `0 0 9 * * MON-FRI` | Weekdays at 9 AM UTC |
| `0 */5 * * * *` | Every 5 minutes |

### Get task status

```bash
curl http://localhost:8080/api/v1/tasks/{taskId}
```

### Cancel a task

```bash
curl -X DELETE http://localhost:8080/api/v1/tasks/{taskId}
```

## Connecting to the Database

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `5433` |
| Database | `task_scheduler` |
| Username | `postgres` |
| Password | `postgres` |

Works with Sequel Ace, DBeaver, pgAdmin, or the CLI:

```bash
psql -h localhost -p 5433 -U postgres -d task_scheduler
```

## Key Design Decisions

| Concern | Decision |
|---|---|
| Source of truth | PostgreSQL — all task state persists here |
| Fast scheduling index | Redis Sorted Sets (time buckets) — ephemeral, rebuildable from DB |
| Execution queue | Kafka topic with manual offset commit for at-least-once delivery |
| Distributed lock | Redis SETNX with TTL per bucket — prevents double-processing |
| Recurring tasks | Cron expression + compute-next-on-completion |
| Retries | Exponential backoff, re-insert into future time bucket |
| Failure recovery | RecurringTaskPromoter + stuck-task recovery scan |

## Scaling

Run multiple instances of the application. The distributed locking on time buckets ensures no task is processed more than once. Kafka consumer groups automatically distribute execution work across instances. Each instance runs both poller and worker threads.

## Logs

Logs are written to both console and `logs/` directory:

- `logs/distributed-task-scheduler.log` — all application logs (rolling, 50MB/file, 30 days)
- `logs/task-audit.log` — task lifecycle events only (create/execute/complete/fail)

All log events use structured `[EVENT_TAG] key=value` format for easy grep/filtering:

```bash
grep '\[TASK_COMPLETED\]' logs/task-audit.log
grep '\[TASK_PERMANENTLY_FAILED\]' logs/task-audit.log
grep '\[POLLER_ENQUEUE\]' logs/distributed-task-scheduler.log
```
