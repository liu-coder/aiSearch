package com.aisearch.search.infrastructure;

import com.aisearch.common.search.RecallSource;
import com.aisearch.search.application.RecallAdapter;
import com.aisearch.search.domain.CandidateSegment;
import com.aisearch.search.domain.QueryIntent;
import com.aisearch.search.infrastructure.config.SearchBackendProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * Milvus REST 召回适配器，负责文本向量、图片向量和片段向量检索。
 */
public class MilvusRecallAdapter implements RecallAdapter {
    private final RecallSource source;
    private final RestClient restClient;
    private final SearchBackendProperties properties;
    private final ModelEmbeddingClient modelEmbeddingClient;

    public MilvusRecallAdapter(
            RecallSource source,
            RestClient restClient,
            SearchBackendProperties properties,
            ModelEmbeddingClient modelEmbeddingClient) {
        this.source = source;
        this.restClient = restClient;
        this.properties = properties;
        this.modelEmbeddingClient = modelEmbeddingClient;
    }

    @Override
    public RecallSource source() {
        return source;
    }

    @Override
    public List<CandidateSegment> recall(QueryIntent intent, int limit) {
        List<Double> vector = source == RecallSource.IMAGE_VECTOR
                ? modelEmbeddingClient.embedImage(intent.imageUrl())
                : modelEmbeddingClient.embed(queryForEmbedding(intent));
        Map<String, Object> response = restClient.post()
                .uri("/v2/vectordb/entities/search")
                .body(searchBody(vector, limit))
                .retrieve()
                .body(Map.class);
        return parseData(response);
    }

    private String queryForEmbedding(QueryIntent intent) {
        if (source == RecallSource.IMAGE_VECTOR && intent.imageUrl() != null) {
            return "image:" + intent.imageUrl();
        }
        return intent.semanticQuery();
    }

    private Map<String, Object> searchBody(List<Double> vector, int limit) {
        SearchBackendProperties.Milvus milvus = properties.getMilvus();
        return Map.of(
                "collectionName", milvus.getCollectionName(),
                "data", List.of(vector),
                "annsField", source == RecallSource.IMAGE_VECTOR ? milvus.getImageVectorField() : milvus.getVectorField(),
                "limit", limit,
                "searchParams", Map.of("metric_type", milvus.getMetricType()),
                "outputFields", milvus.getOutputFields());
    }

    @SuppressWarnings("unchecked")
    private List<CandidateSegment> parseData(Map<String, Object> response) {
        if (response == null || response.get("data") == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = (List<Map<String, Object>>) response.get("data");
        List<CandidateSegment> candidates = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            double score = score(row.getOrDefault("distance", row.get("score")));
            candidates.add(SearchDocumentMapper.toCandidate(row, score, source));
        }
        return candidates;
    }

    private double score(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }
}
