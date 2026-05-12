package com.aisearch.model.infrastructure;

import com.aisearch.model.application.EmbeddingProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 根据配置选择 Embedding provider。
 */
@Configuration
@EnableConfigurationProperties(ModelProviderProperties.class)
public class ModelProviderConfig {
    @Bean
    @ConditionalOnProperty(prefix = "ai-search.models", name = "provider", havingValue = "http")
    EmbeddingProvider httpEmbeddingProvider(RestClient.Builder builder, ModelProviderProperties properties) {
        return new HttpEmbeddingProvider(builder.build(), properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai-search.models", name = "provider", havingValue = "dashscope")
    EmbeddingProvider dashScopeEmbeddingProvider(RestClient.Builder builder, ModelProviderProperties properties) {
        return new DashScopeEmbeddingProvider(builder.build(), properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai-search.models", name = "provider", havingValue = "deterministic", matchIfMissing = true)
    EmbeddingProvider deterministicEmbeddingProvider(ModelProviderProperties properties) {
        return new DeterministicEmbeddingProvider(properties);
    }
}
