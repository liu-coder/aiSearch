package com.aisearch.video.application;

import com.aisearch.common.video.InitiateUploadRequest;
import com.aisearch.common.video.CompleteUploadRequest;
import com.aisearch.common.video.VideoAssetResponse;
import com.aisearch.common.video.VideoAssetStatus;
import com.aisearch.video.domain.VideoAssetEntity;
import com.aisearch.video.infrastructure.event.VideoUploadedEvent;
import com.aisearch.video.infrastructure.persistence.VideoAssetRepository;
import com.aisearch.video.infrastructure.storage.MinioProperties;
import com.aisearch.video.infrastructure.storage.ObjectStorageService;
import com.aisearch.video.infrastructure.storage.StoredObject;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 视频上传应用服务，负责生成上传地址、落库资产记录、确认上传完成并触发离线处理事件。
 */
@Service
public class VideoUploadService {
    private final VideoAssetRepository videoAssetRepository;
    private final ObjectStorageService objectStorageService;
    private final MinioProperties minioProperties;
    private final VideoUploadOutboxService outboxService;

    public VideoUploadService(
            VideoAssetRepository videoAssetRepository,
            ObjectStorageService objectStorageService,
            MinioProperties minioProperties,
            VideoUploadOutboxService outboxService) {
        this.videoAssetRepository = videoAssetRepository;
        this.objectStorageService = objectStorageService;
        this.minioProperties = minioProperties;
        this.outboxService = outboxService;
    }

    @Transactional
    public VideoAssetResponse initiate(InitiateUploadRequest request) {
        /*
         * 初始化上传分三步：
         * 1. 生成稳定 videoId 和只属于该视频的 MinIO objectKey，避免客户端自定义路径造成覆盖或越权。
         * 2. 为客户端生成短期有效的预签名 PUT URL，此时文件还没有进入对象存储。
         * 3. 先把资产记录保存为 INITIATED，后续 complete 阶段会基于这条记录校验真实对象并推进状态。
         */
        String videoId = "video-" + UUID.randomUUID();
        // 对文件名做最小清洗，避免对象 key 中出现路径分隔符或特殊字符。
        String safeName = request.fileName().replaceAll("[^a-zA-Z0-9._-]", "_");
        String objectKey = "raw/%s/%d-%s".formatted(videoId, Instant.now().toEpochMilli(), safeName);
        String bucket = minioProperties.getRawBucket();
        Duration expire = Duration.ofMinutes(minioProperties.getPresignedUploadExpireMinutes());
        String uploadUrl = objectStorageService.createPresignedPutUrl(bucket, objectKey, request.contentType(), expire);

        VideoAssetEntity asset = VideoAssetEntity.initiated(
                videoId,
                bucket,
                objectKey,
                request.fileName(),
                request.fileSize(),
                request.contentType(),
                request.title());
        videoAssetRepository.save(asset);

        return new VideoAssetResponse(
                videoId,
                objectKey,
                bucket,
                uploadUrl,
                VideoAssetStatus.INITIATED,
                "使用 uploadUrl PUT 上传文件，完成后调用 /api/videos/{videoId}/complete");
    }

    @Transactional
    public VideoAssetResponse complete(String videoId, CompleteUploadRequest request) {
        /*
         * 完成上传确认分四步：
         * 1. 读取 INITIATED 阶段保存的视频资产，拿到服务端生成的 bucket/objectKey。
         * 2. 调 MinIO statObject 复核对象确实已经由客户端直传成功。
         * 3. 更新资产状态为 PROCESSING，并保存最终 size/etag 等上传事实。
         * 4. 写入 outbox 事件，让事务提交后由后台发布器异步投递 RocketMQ。
         */
        VideoAssetEntity asset = videoAssetRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("视频资产不存在：" + videoId));
        StoredObject storedObject = objectStorageService.statObject(asset.getBucket(), asset.getObjectKey());
        asset.markUploaded(
                request.objectETag() == null ? storedObject.etag() : request.objectETag(),
                request.fileSize() == null ? storedObject.size() : request.fileSize());
        videoAssetRepository.save(asset);

        outboxService.appendUploaded(new VideoUploadedEvent(
                asset.getVideoId(),
                asset.getBucket(),
                asset.getObjectKey(),
                asset.getFileSize(),
                asset.getContentType()));

        return new VideoAssetResponse(
                asset.getVideoId(),
                asset.getObjectKey(),
                asset.getBucket(),
                null,
                VideoAssetStatus.PROCESSING,
                "上传已确认，已进入离线视频处理流程");
    }
}
