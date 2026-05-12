package com.aisearch.worker.domain;

import com.aisearch.common.workflow.WorkflowStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 视频离线处理阶段任务，每一行代表一个可独立重试和观测的处理步骤。
 */
@Entity
@Table(
        name = "video_processing_stage_task",
        uniqueConstraints = @UniqueConstraint(name = "uk_video_stage_event_stage", columnNames = {"event_id", "stage"}))
public class VideoProcessingStageTaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 80)
    private String eventId;

    @Column(name = "video_id", nullable = false, length = 80)
    private String videoId;

    @Column(nullable = false, length = 128)
    private String bucket;

    @Column(nullable = false, length = 512)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorkflowStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private StageTaskStatus status;

    @Column(name = "stage_sequence", nullable = false)
    private int stageSequence;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", length = 40)
    private StageFailureType failureType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VideoProcessingStageTaskEntity() {
    }

    private VideoProcessingStageTaskEntity(
            String eventId,
            String videoId,
            String bucket,
            String objectKey,
            WorkflowStage stage,
            StageTaskStatus status,
            int stageSequence) {
        this.eventId = eventId;
        this.videoId = videoId;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.stage = stage;
        this.status = status;
        this.stageSequence = stageSequence;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static VideoProcessingStageTaskEntity planned(
            String eventId,
            String videoId,
            String bucket,
            String objectKey,
            WorkflowStage stage,
            StageTaskStatus status,
            int stageSequence) {
        return new VideoProcessingStageTaskEntity(eventId, videoId, bucket, objectKey, stage, status, stageSequence);
    }

    public WorkflowStage getStage() {
        return stage;
    }

    public StageTaskStatus getStatus() {
        return status;
    }

    public int getStageSequence() {
        return stageSequence;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getBucket() {
        return bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public int getAttempts() {
        return attempts;
    }

    public StageFailureType getFailureType() {
        return failureType;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void forcePending(String reason) {
        this.status = StageTaskStatus.PENDING;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 1000));
        this.updatedAt = Instant.now();
    }

    public void markRunning() {
        this.status = StageTaskStatus.RUNNING;
        this.attempts += 1;
        this.failureReason = null;
        this.failureType = null;
        this.updatedAt = Instant.now();
    }

    public void markSucceeded() {
        this.status = StageTaskStatus.SUCCEEDED;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        markFailed(StageFailureType.UNKNOWN, reason);
    }

    public void markFailed(StageFailureType failureType, String reason) {
        this.status = StageTaskStatus.FAILED;
        this.failureType = failureType;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 1000));
        this.updatedAt = Instant.now();
    }

    public void markPendingForRetry(String reason) {
        markPendingForRetry(StageFailureType.UNKNOWN, reason);
    }

    public void markPendingForRetry(StageFailureType failureType, String reason) {
        this.status = StageTaskStatus.PENDING;
        this.failureType = failureType;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 1000));
        this.updatedAt = Instant.now();
    }

    public void resetForRerun() {
        this.status = StageTaskStatus.PENDING;
        this.failureType = null;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }
}
