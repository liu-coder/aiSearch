package com.aisearch.video.infrastructure.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/**
 * MinIO 对象存储实现，负责创建 bucket、生成上传 URL、校验对象是否存在。
 */
@Service
public class MinioObjectStorageService implements ObjectStorageService {
    private final MinioClient minioClient;

    public MinioObjectStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public String createPresignedPutUrl(String bucket, String objectKey, String contentType, Duration expire) {
        try {
            ensureBucket(bucket);
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry((int) expire.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("创建 MinIO 预签名上传 URL 失败", ex);
        }
    }

    @Override
    public StoredObject statObject(String bucket, String objectKey) {
        try {
            StatObjectResponse response = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return new StoredObject(bucket, objectKey, response.size(), response.etag(), response.contentType());
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO 中未找到已上传对象：" + bucket + "/" + objectKey, ex);
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
