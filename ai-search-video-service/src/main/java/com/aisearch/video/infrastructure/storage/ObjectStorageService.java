package com.aisearch.video.infrastructure.storage;

import java.time.Duration;

/**
 * 对象存储端口，屏蔽 MinIO/OSS 等实现差异。
 */
public interface ObjectStorageService {
    String createPresignedPutUrl(String bucket, String objectKey, String contentType, Duration expire);

    StoredObject statObject(String bucket, String objectKey);
}
