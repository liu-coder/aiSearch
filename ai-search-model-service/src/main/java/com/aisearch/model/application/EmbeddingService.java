package com.aisearch.model.application;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Embedding 服务边界，统一封装本地确定性 provider 和外部模型 provider。
 */
@Service
public class EmbeddingService {
    private final EmbeddingProvider provider;

    public EmbeddingService(EmbeddingProvider provider) {
        this.provider = provider;
    }

    public List<Double> embedText(String text) {
        return embed(new EmbeddingRequest(text, "text"));
    }

    public List<Double> embed(EmbeddingRequest request) {
        return provider.embed(request);
    }
}
