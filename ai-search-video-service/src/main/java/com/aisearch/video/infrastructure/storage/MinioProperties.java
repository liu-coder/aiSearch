package com.aisearch.video.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 配置，复用 docker 环境中的对象存储服务。
 */
@ConfigurationProperties(prefix = "ai-search.storage.minio")
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String rawBucket;
    private long presignedUploadExpireMinutes = 30;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRawBucket() {
        return rawBucket;
    }

    public void setRawBucket(String rawBucket) {
        this.rawBucket = rawBucket;
    }

    public long getPresignedUploadExpireMinutes() {
        return presignedUploadExpireMinutes;
    }

    public void setPresignedUploadExpireMinutes(long presignedUploadExpireMinutes) {
        this.presignedUploadExpireMinutes = presignedUploadExpireMinutes;
    }
}
