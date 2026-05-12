package com.aisearch.worker.infrastructure.storage;

import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/**
 * Worker 对象存储服务，负责下载原始视频、上传离线产物并生成模型可访问的临时 URL。
 */
@Service
public class WorkerObjectStorageService {
    private final MinioClient minioClient;
    private final WorkerPipelineProperties properties;

    public WorkerObjectStorageService(MinioClient minioClient, WorkerPipelineProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 下载原始对象到本地工作目录，供 FFmpeg 进行离线处理。
     */
    public void download(String bucket, String objectKey, Path target) {
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build())) {
                Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("下载对象失败: " + bucket + "/" + objectKey, ex);
        }
    }

    /**
     * 上传音频、关键帧等处理产物；bucket 不存在时自动创建，便于本地环境初始化。
     */
    public void upload(String bucket, String objectKey, Path source, String contentType) {
        try {
            ensureBucket(bucket);
            try (InputStream input = Files.newInputStream(source)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .contentType(contentType)
                        .stream(input, Files.size(source), -1)
                        .build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("上传对象失败: " + bucket + "/" + objectKey, ex);
        }
    }

    /**
     * 生成临时读取 URL，传给 DashScope 侧 ASR/OCR/Caption 模型读取媒体文件。
     */
    public String presignedGetUrl(String bucket, String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(properties.getStorage().getPresignedExpireMinutes(), TimeUnit.MINUTES)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("生成对象临时访问地址失败: " + bucket + "/" + objectKey, ex);
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
