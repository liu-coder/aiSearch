package com.aisearch.worker.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Worker pipeline 配置装配。
 */
@Configuration
@EnableConfigurationProperties(WorkerPipelineProperties.class)
public class WorkerPipelineConfig {
}
