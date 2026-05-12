package com.aisearch.video.infrastructure.event;

/**
 * 视频上传完成领域事件，由 publisher 转换为跨服务 MQ 消息。
 */
public record VideoUploadedEvent(
        String videoId,
        String bucket,
        String objectKey,
        long fileSize,
        String contentType
) {
}
