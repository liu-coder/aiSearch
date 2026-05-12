package com.aisearch.video.domain;

import com.aisearch.common.video.VideoAssetStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 视频资产持久化实体，记录对象存储位置和离线处理状态。
 */
@Entity
@Table(name = "video_asset")
public class VideoAssetEntity {
    @Id
    @Column(length = 80)
    private String videoId;

    @Column(nullable = false, length = 128)
    private String bucket;

    @Column(nullable = false, length = 512)
    private String objectKey;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false)
    private long fileSize;

    @Column(length = 120)
    private String contentType;

    @Column(length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VideoAssetStatus status;

    @Column(name = "object_etag", length = 200)
    private String objectETag;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected VideoAssetEntity() {
    }

    private VideoAssetEntity(
            String videoId,
            String bucket,
            String objectKey,
            String fileName,
            long fileSize,
            String contentType,
            String title) {
        this.videoId = videoId;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.title = title;
        this.status = VideoAssetStatus.INITIATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static VideoAssetEntity initiated(
            String videoId,
            String bucket,
            String objectKey,
            String fileName,
            long fileSize,
            String contentType,
            String title) {
        return new VideoAssetEntity(videoId, bucket, objectKey, fileName, fileSize, contentType, title);
    }

    public void markUploaded(String objectETag, long uploadedSize) {
        this.objectETag = objectETag;
        this.fileSize = uploadedSize;
        this.status = VideoAssetStatus.PROCESSING;
        this.updatedAt = Instant.now();
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

    public String getContentType() {
        return contentType;
    }

    public String getTitle() {
        return title;
    }

    public VideoAssetStatus getStatus() {
        return status;
    }
}
