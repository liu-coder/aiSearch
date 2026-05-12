package com.aisearch.worker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 片段级产物实体，结构化保存 ASR/OCR/Caption/Frame 等检索证据。
 */
@Entity
@Table(
        name = "video_segment_artifact",
        uniqueConstraints = @UniqueConstraint(name = "uk_segment_artifact_type", columnNames = {"segment_id", "artifact_type"}))
public class VideoSegmentArtifactEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false, length = 80)
    private String videoId;

    @Column(name = "segment_id", nullable = false, length = 120)
    private String segmentId;

    @Column(name = "artifact_type", nullable = false, length = 60)
    private String artifactType;

    @Column(nullable = false, columnDefinition = "mediumtext")
    private String payload;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VideoSegmentArtifactEntity() {
    }

    private VideoSegmentArtifactEntity(String videoId, String segmentId, String artifactType, String payload) {
        this.videoId = videoId;
        this.segmentId = segmentId;
        this.artifactType = artifactType;
        this.payload = payload;
        this.updatedAt = Instant.now();
    }

    public static VideoSegmentArtifactEntity of(String videoId, String segmentId, String artifactType, String payload) {
        return new VideoSegmentArtifactEntity(videoId, segmentId, artifactType, payload);
    }

    public void replacePayload(String payload) {
        this.payload = payload;
        this.updatedAt = Instant.now();
    }

    public String getSegmentId() {
        return segmentId;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public String getPayload() {
        return payload;
    }
}
