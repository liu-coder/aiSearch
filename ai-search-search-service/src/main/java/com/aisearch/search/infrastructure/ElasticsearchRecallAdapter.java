package com.aisearch.search.infrastructure;

import com.aisearch.common.search.RecallSource;
import com.aisearch.search.application.RecallAdapter;
import com.aisearch.search.domain.CandidateSegment;
import com.aisearch.search.domain.QueryIntent;
import com.aisearch.search.infrastructure.config.SearchBackendProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * Elasticsearch 召回适配器，承担关键词、ASR、OCR 和元数据倒排检索。
 */
public class ElasticsearchRecallAdapter implements RecallAdapter {
    private final RecallSource source;
    private final RestClient restClient;
    private final SearchBackendProperties properties;

    public ElasticsearchRecallAdapter(
            RecallSource source,
            RestClient restClient,
            SearchBackendProperties properties) {
        this.source = source;
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public RecallSource source() {
        return source;
    }

    @Override
    public List<CandidateSegment> recall(QueryIntent intent, int limit) {
        Map<String, Object> response = restClient.post()
                .uri("/{index}/_search", properties.getElasticsearch().getIndex())
                .body(searchBody(intent, limit))
                .retrieve()
                .body(Map.class);
        return parseHits(response);
    }

    private Map<String, Object> searchBody(QueryIntent intent, int limit) {
        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("should", List.of(Map.of("multi_match", Map.of(
                "query", intent.semanticQuery(),
                "fields", fieldsForSource()))));
        bool.put("minimum_should_match", 1);
        List<Map<String, Object>> filters = filters(intent);
        if (!filters.isEmpty()) {
            bool.put("filter", filters);
        }
        return Map.of(
                "size", limit,
                "query", Map.of("bool", bool),
                "_source", List.of("videoId", "video_id", "segmentId", "segment_id", "title",
                        "startTimeMs", "start_time_ms", "endTimeMs", "end_time_ms"));
    }

    private List<String> fieldsForSource() {
        return switch (source) {
            case ASR -> List.of("asrText^3", "title", "caption");
            case OCR -> List.of("ocrText^3", "title", "caption");
            case METADATA -> List.of("title^3", "tags^2", "author", "contentType");
            default -> properties.getElasticsearch().getTextFields();
        };
    }

    private List<Map<String, Object>> filters(QueryIntent intent) {
        List<Map<String, Object>> filters = new ArrayList<>();
        intent.filters().forEach((key, value) -> {
            if ("startDate".equals(key) || "endDate".equals(key)) {
                return;
            }
            filters.add(Map.of("term", Map.of(key, value)));
        });
        if (intent.filters().containsKey("startDate") || intent.filters().containsKey("endDate")) {
            Map<String, Object> range = new LinkedHashMap<>();
            if (intent.filters().containsKey("startDate")) {
                range.put("gte", intent.filters().get("startDate"));
            }
            if (intent.filters().containsKey("endDate")) {
                range.put("lte", intent.filters().get("endDate"));
            }
            filters.add(Map.of("range", Map.of("publishedAt", range)));
        }
        return filters;
    }

    @SuppressWarnings("unchecked")
    private List<CandidateSegment> parseHits(Map<String, Object> response) {
        if (response == null || response.get("hits") == null) {
            return List.of();
        }
        Map<String, Object> hitsContainer = (Map<String, Object>) response.get("hits");
        List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsContainer.getOrDefault("hits", List.of());
        List<CandidateSegment> candidates = new ArrayList<>();
        for (Map<String, Object> hit : hits) {
            Map<String, Object> document = (Map<String, Object>) hit.getOrDefault("_source", Map.of());
            double score = score(hit.get("_score"));
            candidates.add(SearchDocumentMapper.toCandidate(document, score, source));
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
