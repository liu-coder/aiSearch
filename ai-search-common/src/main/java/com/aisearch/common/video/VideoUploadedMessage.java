package com.aisearch.common.video;

import java.time.Instant;

/**
 * 视频上传完成 MQ 消息协议。Video Service 负责发布，Worker Service 负责消费。
 */
public record VideoUploadedMessage(
        String eventId,
        String videoId,
        String bucket,
        String objectKey,
        long fileSize,
        String contentType,
        Instant occurredAt
) {
}
