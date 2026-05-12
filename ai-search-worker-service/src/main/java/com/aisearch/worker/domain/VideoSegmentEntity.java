package com.aisearch.worker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 视频片段结构化实体，承载检索最小粒度的时间范围和切片策略。
 */
@Entity
@Table(name = "video_segment")
public class VideoSegmentEntity {
    @Id
    @Column(name = "segment_id", length = 120)
    private String segmentId;

    @Column(name = "video_id", nullable = false, length = 80)
    private String videoId;

    @Column(name = "start_time_ms", nullable = false)
    private long startTimeMs;

    @Column(name = "end_time_ms", nullable = false)
    private long endTimeMs;

    @Column(name = "key_frame_time_ms", nullable = false)
    private long keyFrameTimeMs;

    @Column(name = "strategy_name", nullable = false, length = 80)
    private String strategyName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VideoSegmentEntity() {
    }

    private VideoSegmentEntity(String videoId, VideoSegmentPlanLike segment, String strategyName) {
        this.segmentId = segment.segmentId();
        this.videoId = videoId;
        replace(segment, strategyName);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static VideoSegmentEntity of(String videoId, VideoSegmentPlanLike segment, String strategyName) {
        return new VideoSegmentEntity(videoId, segment, strategyName);
    }

    public void replace(VideoSegmentPlanLike segment, String strategyName) {
        this.startTimeMs = segment.startTimeMs();
        this.endTimeMs = segment.endTimeMs();
        this.keyFrameTimeMs = segment.keyFrameTimeMs();
        this.strategyName = strategyName;
        this.updatedAt = Instant.now();
    }

    public String getSegmentId() {
        return segmentId;
    }

    public String getVideoId() {
        return videoId;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public long getEndTimeMs() {
        return endTimeMs;
    }

    public interface VideoSegmentPlanLike {
        String segmentId();

        long startTimeMs();

        long endTimeMs();

        long keyFrameTimeMs();
    }
}
