package com.aisearch.worker.domain;

import com.aisearch.common.video.VideoAssetStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Worker 只读视频资产视图，复用 video-service 写入的 video_asset 表。
 */
@Entity
@Table(name = "video_asset")
public class VideoAssetReadEntity {
    @Id
    @Column(length = 80)
    private String videoId;

    @Column(nullable = false, length = 128)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private long fileSize;

    @Column(length = 255)
    private String title;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private VideoAssetStatus status;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected VideoAssetReadEntity() {
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

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getTitle() {
        return title == null || title.isBlank() ? fileName : title;
    }

    public String getContentType() {
        return contentType;
    }

    public VideoAssetStatus getStatus() {
        return status;
    }

    public void markReady() {
        this.status = VideoAssetStatus.READY;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = VideoAssetStatus.FAILED;
        this.updatedAt = Instant.now();
    }
}
