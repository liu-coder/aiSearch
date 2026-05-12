package com.aisearch.search.infrastructure.config;

import com.aisearch.common.search.RecallSource;
import com.aisearch.search.application.RecallAdapter;
import com.aisearch.search.infrastructure.ElasticsearchRecallAdapter;
import com.aisearch.search.infrastructure.MilvusRecallAdapter;
import com.aisearch.search.infrastructure.ModelEmbeddingClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 搜索后端装配配置，按配置开关注册 ES/Milvus 召回适配器。
 */
@Configuration
@EnableConfigurationProperties(SearchBackendProperties.class)
public class SearchBackendConfig {
    @Bean
    ModelEmbeddingClient modelEmbeddingClient(RestClient.Builder builder, SearchBackendProperties properties) {
        return new ModelEmbeddingClient(builder.baseUrl(properties.getModel().getEndpoint()).build());
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai-search.search.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
    RecallAdapter keywordRecallAdapter(RestClient.Builder builder, SearchBackendProperties properties) {
        return new ElasticsearchRecallAdapter(
                RecallSource.KEYWORD,
                builder.baseUrl(properties.getElasticsearch().getEndpoint()).build(),
                properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai-search.search.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
    RecallAdapter asrRecallAdapter(RestClient.Builder builder, SearchBackendProperties properties) {
        return new ElasticsearchRecallAdapter(
                RecallSource.ASR,
                builder.baseUrl(properties.getElasticsearch().getEndpoint()).build(),
                properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai-search.search.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
    RecallAdapter ocrRecallAdapter(RestClient.Builder builder, SearchBackendProperties properties) {
        return new ElasticsearchRecallAdapter(
                RecallSource.OCR,
                builder.baseUrl(properties.getElasticsearch().getEndpoint()).build(),
                properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai-search.search.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
    RecallAdapter metadataRecallAdapter(RestClient.Builder builder, SearchBackendProperties properties) {
        return new ElasticsearchRecallAdapter(
                RecallSource.METADATA,
                builder.baseUrl(properties.getElasticsearch().getEndpoint()).build(),
                properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai-search.search.milvus", name = "enabled", havingValue = "true", matchIfMissing = true)
    RecallAdapter textVectorRecallAdapter(
            RestClient.Builder builder,
            SearchBackendProperties properties,
            ModelEmbeddingClient modelEmbeddingClient) {
        return new MilvusRecallAdapter(
                RecallSource.TEXT_VECTOR,
                builder.baseUrl(properties.getMilvus().getEndpoint()).build(),
                properties,
                modelEmbeddingClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai-search.search.milvus", name = "enabled", havingValue = "true", matchIfMissing = true)
    RecallAdapter imageVectorRecallAdapter(
            RestClient.Builder builder,
            SearchBackendProperties properties,
            ModelEmbeddingClient modelEmbeddingClient) {
        return new MilvusRecallAdapter(
                RecallSource.IMAGE_VECTOR,
                builder.baseUrl(properties.getMilvus().getEndpoint()).build(),
                properties,
                modelEmbeddingClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai-search.search.milvus", name = "enabled", havingValue = "true", matchIfMissing = true)
    RecallAdapter segmentVectorRecallAdapter(
            RestClient.Builder builder,
            SearchBackendProperties properties,
            ModelEmbeddingClient modelEmbeddingClient) {
        return new MilvusRecallAdapter(
                RecallSource.SEGMENT_VECTOR,
                builder.baseUrl(properties.getMilvus().getEndpoint()).build(),
                properties,
                modelEmbeddingClient);
    }
}
