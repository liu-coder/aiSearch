package com.aisearch.worker.application;

/**
 * 本地媒体处理后的对象存储产物。
 */
public record MediaArtifact(String bucket, String objectKey, String url) {
}
