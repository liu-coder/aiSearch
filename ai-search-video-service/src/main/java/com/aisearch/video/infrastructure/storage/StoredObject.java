package com.aisearch.video.infrastructure.storage;

/**
 * 对象存储中的实际文件信息。
 */
public record StoredObject(
        String bucket,
        String objectKey,
        long size,
        String etag,
        String contentType
) {
}
