package com.aisearch.worker.infrastructure.persistence;

import com.aisearch.worker.domain.VideoProcessingStageTaskEntity;
import com.aisearch.worker.domain.StageTaskStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 阶段任务数据库仓储，支持按事件幂等判断和按视频查看处理计划。
 */
public interface VideoProcessingStageTaskRepository extends JpaRepository<VideoProcessingStageTaskEntity, Long> {
    boolean existsByEventId(String eventId);

    List<VideoProcessingStageTaskEntity> findByVideoIdOrderByStageSequenceAsc(String videoId);

    List<VideoProcessingStageTaskEntity> findTop20ByStatusOrderByCreatedAtAsc(StageTaskStatus status);

    List<VideoProcessingStageTaskEntity> findTop20ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            StageTaskStatus status,
            Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update VideoProcessingStageTaskEntity t
            set t.status = com.aisearch.worker.domain.StageTaskStatus.RUNNING,
                t.attempts = t.attempts + 1,
                t.failureReason = null,
                t.updatedAt = :now
            where t.id = :taskId
              and t.status = com.aisearch.worker.domain.StageTaskStatus.PENDING
            """)
    int claimPendingTask(@Param("taskId") Long taskId, @Param("now") Instant now);

    @Query("""
            select count(t) = 0
            from VideoProcessingStageTaskEntity t
            where t.eventId = :eventId
              and t.stageSequence < :stageSequence
              and t.status <> com.aisearch.worker.domain.StageTaskStatus.SUCCEEDED
            """)
    boolean previousStagesSucceeded(@Param("eventId") String eventId, @Param("stageSequence") int stageSequence);

    @Query("""
            select count(t) = 0
            from VideoProcessingStageTaskEntity t
            where t.eventId = :eventId
              and t.status <> com.aisearch.worker.domain.StageTaskStatus.SUCCEEDED
            """)
    boolean allStagesSucceeded(@Param("eventId") String eventId);
}
