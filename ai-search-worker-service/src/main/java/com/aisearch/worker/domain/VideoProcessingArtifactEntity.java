package com.aisearch.worker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 离线处理阶段产物，保存 ASR/OCR/Caption/Embedding/索引文档等中间结果。
 */
@Entity
@Table(
        name = "video_processing_artifact",
        uniqueConstraints = @UniqueConstraint(name = "uk_video_artifact_type", columnNames = {"video_id", "artifact_type"}))
public class VideoProcessingArtifactEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false, length = 80)
    private String videoId;

    @Column(name = "artifact_type", nullable = false, length = 60)
    private String artifactType;

    @Lob
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VideoProcessingArtifactEntity() {
    }

    private VideoProcessingArtifactEntity(String videoId, String artifactType, String payload) {
        this.videoId = videoId;
        this.artifactType = artifactType;
        this.payload = payload;
        this.updatedAt = Instant.now();
    }

    public static VideoProcessingArtifactEntity of(String videoId, String artifactType, String payload) {
        return new VideoProcessingArtifactEntity(videoId, artifactType, payload);
    }

    public void replacePayload(String payload) {
        this.payload = payload;
        this.updatedAt = Instant.now();
    }

    public String getArtifactType() {
        return artifactType;
    }

    public String getPayload() {
        return payload;
    }
}
