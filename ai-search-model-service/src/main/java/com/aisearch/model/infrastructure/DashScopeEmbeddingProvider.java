package com.aisearch.model.infrastructure;

import com.aisearch.model.application.EmbeddingProvider;
import com.aisearch.model.application.EmbeddingRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * 阿里云百炼 DashScope Embedding provider，使用 OpenAI 兼容 embeddings 接口。
 */
public class DashScopeEmbeddingProvider implements EmbeddingProvider {
    private final RestClient restClient;
    private final ModelProviderProperties properties;

    public DashScopeEmbeddingProvider(RestClient restClient, ModelProviderProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Double> embed(EmbeddingRequest request) {
        /*
         * DashScope 向量调用流程：
         * 1. 根据 sourceType 选择文本向量或多模态向量模型。
         * 2. 使用百炼 OpenAI 兼容 embeddings endpoint，便于后续替换模型版本。
         * 3. 从 data[0].embedding 解析向量，保持对搜索/worker 的统一返回结构。
         */
        Map<String, Object> response = restClient.post()
                .uri(properties.getDashscope().getEmbeddingEndpoint())
                .headers(headers -> headers.setBearerAuth(requiredApiKey()))
                .body(Map.of(
                        "model", modelFor(request),
                        "input", request.input(),
                        "dimensions", properties.getDashscope().getEmbeddingDimension(),
                        "encoding_format", "float"))
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("data") == null) {
            throw new IllegalStateException("DashScope embedding 返回缺少 data 字段");
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        if (data.isEmpty() || data.get(0).get("embedding") == null) {
            throw new IllegalStateException("DashScope embedding 返回缺少 embedding 字段");
        }
        return (List<Double>) data.get(0).get("embedding");
    }

    private String modelFor(EmbeddingRequest request) {
        if ("image".equalsIgnoreCase(request.normalizedSourceType())) {
            return properties.getDashscope().getMultimodalEmbeddingModel();
        }
        return properties.getDashscope().getTextEmbeddingModel();
    }

    private String requiredApiKey() {
        String apiKey = properties.getDashscope().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 ai-search.models.dashscope.api-key");
        }
        return apiKey;
    }
}
