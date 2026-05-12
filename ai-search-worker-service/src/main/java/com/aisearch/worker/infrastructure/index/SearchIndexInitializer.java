package com.aisearch.worker.infrastructure.index;

import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Worker 启动时初始化 ES index 和 Milvus collection，避免首条索引任务因结构缺失失败。
 */
@Component
public class SearchIndexInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SearchIndexInitializer.class);

    private final WorkerPipelineProperties properties;
    private final RestClient elasticsearchClient;
    private final RestClient milvusClient;

    public SearchIndexInitializer(RestClient.Builder builder, WorkerPipelineProperties properties) {
        this.properties = properties;
        this.elasticsearchClient = builder.baseUrl(properties.getElasticsearch().getEndpoint()).build();
        this.milvusClient = builder.baseUrl(properties.getMilvus().getEndpoint()).build();
    }

    @Override
    public void run(ApplicationArguments args) {
        /*
         * 启动初始化流程：
         * 1. 检查配置开关，允许生产环境改由独立迁移任务管理索引结构。
         * 2. 确保 Elasticsearch index 存在，缺失时创建 mapping。
         * 3. 确保 Milvus collection 存在，缺失时创建 schema/index 并 load。
         */
        if (!properties.isInitializeIndexOnStartup()) {
            return;
        }
        ensureElasticsearchIndex();
        ensureMilvusCollection();
    }

    private void ensureElasticsearchIndex() {
        /*
         * ES 初始化流程：
         * 1. HEAD index 判断是否存在。
         * 2. 只有 404 才创建；其他异常继续抛出，避免掩盖认证或网络问题。
         * 3. 创建 mapping，固定检索服务依赖的 videoId/segmentId/title/ASR/OCR/Caption 字段。
         */
        String index = properties.getElasticsearch().getIndex();
        try {
            elasticsearchClient.head().uri("/{index}", index).retrieve().toBodilessEntity();
            return;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw ex;
            }
        }
        elasticsearchClient.put()
                .uri("/{index}", index)
                .body(elasticsearchMapping())
                .retrieve()
                .toBodilessEntity();
        log.info("elasticsearch_index_created index={}", index);
    }

    private Map<String, Object> elasticsearchMapping() {
        return Map.of(
                "mappings", Map.of(
                        "properties", Map.of(
                                "videoId", Map.of("type", "keyword"),
                                "segmentId", Map.of("type", "keyword"),
                                "title", Map.of("type", "text", "analyzer", "standard"),
                                "startTimeMs", Map.of("type", "long"),
                                "endTimeMs", Map.of("type", "long"),
                                "asrText", Map.of("type", "text", "analyzer", "standard"),
                                "ocrText", Map.of("type", "text", "analyzer", "standard"),
                                "caption", Map.of("type", "text", "analyzer", "standard"),
                                "indexVersion", Map.of("type", "keyword"))));
    }

    @SuppressWarnings("unchecked")
    private void ensureMilvusCollection() {
        /*
         * Milvus 初始化流程：
         * 1. describe collection 判断是否已经存在。
         * 2. 不存在时按 embedding 维度、主键字段、向量字段和 metric 创建 collection。
         * 3. 创建后立即 load，保证后续 search/insert 链路能直接使用。
         */
        WorkerPipelineProperties.Milvus milvus = properties.getMilvus();
        Map<String, Object> response = milvusClient.post()
                .uri("/v2/vectordb/collections/describe")
                .headers(headers -> addMilvusToken(headers, milvus))
                .body(Map.of("collectionName", milvus.getCollectionName()))
                .retrieve()
                .body(Map.class);
        if (response != null && Integer.valueOf(0).equals(response.get("code"))) {
            return;
        }
        milvusClient.post()
                .uri("/v2/vectordb/collections/create")
                .headers(headers -> addMilvusToken(headers, milvus))
                .body(milvusCreateBody(milvus))
                .retrieve()
                .toBodilessEntity();
        milvusClient.post()
                .uri("/v2/vectordb/collections/load")
                .headers(headers -> addMilvusToken(headers, milvus))
                .body(Map.of("collectionName", milvus.getCollectionName()))
                .retrieve()
                .toBodilessEntity();
        log.info("milvus_collection_created collection={}", milvus.getCollectionName());
    }

    private Map<String, Object> milvusCreateBody(WorkerPipelineProperties.Milvus milvus) {
        return Map.of(
                "collectionName", milvus.getCollectionName(),
                "dimension", properties.getEmbeddingDimension(),
                "primaryFieldName", milvus.getPrimaryField(),
                "idType", "VarChar",
                "vectorFieldName", milvus.getVectorField(),
                "metricType", milvus.getMetricType(),
                "enableDynamicField", true,
                "params", Map.of("max_length", 128),
                "indexParams", List.of(Map.of(
                        "fieldName", milvus.getVectorField(),
                        "indexName", milvus.getVectorField() + "_idx",
                        "metricType", milvus.getMetricType(),
                        "indexType", "AUTOINDEX"),
                        Map.of(
                                "fieldName", milvus.getImageVectorField(),
                                "indexName", milvus.getImageVectorField() + "_idx",
                                "metricType", milvus.getMetricType(),
                                "indexType", "AUTOINDEX")));
    }

    private void addMilvusToken(org.springframework.http.HttpHeaders headers, WorkerPipelineProperties.Milvus milvus) {
        if (milvus.getToken() != null && !milvus.getToken().isBlank()) {
            headers.setBearerAuth(milvus.getToken());
        }
    }
}
