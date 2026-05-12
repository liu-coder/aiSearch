package com.aisearch.worker.infrastructure.storage;

import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Worker 侧 MinIO 客户端配置，复用 video-service 写入的对象存储。
 */
@Configuration
public class WorkerMinioConfig {
    @Bean
    MinioClient workerMinioClient(WorkerPipelineProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getStorage().getEndpoint())
                .credentials(properties.getStorage().getAccessKey(), properties.getStorage().getSecretKey())
                .build();
    }
}
