package com.aisearch.model.infrastructure;

import com.aisearch.model.application.EmbeddingProvider;
import com.aisearch.model.application.EmbeddingRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * HTTP provider，适配自建 BGE/CLIP 服务或厂商模型网关。
 */
public class HttpEmbeddingProvider implements EmbeddingProvider {
    private final RestClient restClient;
    private final ModelProviderProperties properties;

    public HttpEmbeddingProvider(RestClient restClient, ModelProviderProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Double> embed(EmbeddingRequest request) {
        Map<String, Object> response = restClient.post()
                .uri(properties.getHttp().getEndpoint())
                .headers(headers -> {
                    if (properties.getHttp().getApiKey() != null && !properties.getHttp().getApiKey().isBlank()) {
                        headers.setBearerAuth(properties.getHttp().getApiKey());
                    }
                })
                .body(Map.of(
                        "model", properties.getHttp().getModel(),
                        "input", request.input(),
                        "sourceType", request.normalizedSourceType()))
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("embedding") == null) {
            throw new IllegalStateException("外部 embedding 服务返回缺少 embedding 字段");
        }
        return (List<Double>) response.get("embedding");
    }
}
