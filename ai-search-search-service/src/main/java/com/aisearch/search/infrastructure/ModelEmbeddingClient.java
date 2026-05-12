package com.aisearch.search.infrastructure;

import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * 模型服务客户端，负责把在线检索需要的 embedding、rerank、analysis 能力统一转发到 model-service。
 */
public class ModelEmbeddingClient {
    private final RestClient restClient;

    public ModelEmbeddingClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @SuppressWarnings("unchecked")
    public List<Double> embed(String input) {
        Map<String, Object> response = restClient.get()
                .uri("/api/models/embedding?text={text}", input == null ? "" : input)
                .retrieve()
                .body(Map.class);
        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            throw new IllegalStateException("模型服务 embedding 调用失败");
        }
        return (List<Double>) response.get("data");
    }

    @SuppressWarnings("unchecked")
    public List<Double> embedImage(String imageUrl) {
        Object data = postForData("/api/models/image-embedding", Map.of("imageUrl", imageUrl == null ? "" : imageUrl));
        if (data instanceof List<?> values) {
            return values.stream()
                    .map(value -> ((Number) value).doubleValue())
                    .toList();
        }
        throw new IllegalStateException("模型服务 image embedding 返回格式不正确: " + data);
    }

    @SuppressWarnings("unchecked")
    public List<RerankHit> rerank(String query, List<String> documents, int topN) {
        Object data = postForData("/api/models/rerank", Map.of(
                "query", query,
                "documents", documents,
                "topN", topN));
        if (data instanceof List<?> values) {
            return values.stream()
                    .map(value -> toRerankHit((Map<String, Object>) value))
                    .toList();
        }
        throw new IllegalStateException("模型服务 rerank 返回格式不正确: " + data);
    }

    public String analyze(String query, List<String> evidence) {
        Object data = postForData("/api/models/analysis", Map.of("query", query, "evidence", evidence));
        return data == null ? "" : data.toString();
    }

    @SuppressWarnings("unchecked")
    private Object postForData(String path, Object body) {
        /*
         * 在线模型调用步骤：
         * 1. 通过统一 ApiResponse 协议调用 model-service。
         * 2. success=false 或空响应直接抛错，由上层决定降级还是失败。
         * 3. 只返回 data，保持搜索应用层不感知 HTTP 包装结构。
         */
        Map<String, Object> response = restClient.post()
                .uri(path)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            Object message = response == null ? "空响应" : response.get("message");
            throw new IllegalStateException("模型服务调用失败: " + message);
        }
        return response.get("data");
    }

    private RerankHit toRerankHit(Map<String, Object> value) {
        int index = ((Number) value.getOrDefault("index", 0)).intValue();
        double score = ((Number) value.getOrDefault("score", 0.0)).doubleValue();
        return new RerankHit(index, score);
    }

    public record RerankHit(int index, double score) {
    }
}
