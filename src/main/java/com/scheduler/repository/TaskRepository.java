package com.scheduler.repository;

import com.scheduler.model.entity.Task;
import com.scheduler.model.enums.TaskStatus;
import com.scheduler.model.enums.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByTaskId(String taskId);

    Optional<Task> findByTaskIdAndStatus(String taskId, TaskStatus status);

    List<Task> findByTaskTypeAndStatusAndNextExecutionBefore(
            TaskType taskType, TaskStatus status, Instant horizon);

    /**
     * Atomically transition a task from one status to another.
     * Returns the number of rows updated (0 means someone else grabbed it).
     */
    @Transactional
    @Modifying
    @Query("UPDATE Task t SET t.status = :newStatus, t.updatedAt = :now " +
           "WHERE t.taskId = :taskId AND t.status = :expectedStatus")
    int compareAndSetStatus(@Param("taskId") String taskId,
                            @Param("expectedStatus") TaskStatus expectedStatus,
                            @Param("newStatus") TaskStatus newStatus,
                            @Param("now") Instant now);

    /**
     * Find tasks stuck in RUNNING state beyond a timeout threshold (for recovery).
     */
    @Query("SELECT t FROM Task t WHERE t.status = 'RUNNING' AND t.updatedAt < :threshold")
    List<Task> findStuckRunningTasks(@Param("threshold") Instant threshold);
}
