package com.aisearch.model.application;

import java.util.List;

/**
 * Embedding 供应商端口，生产环境可接 DashScope、BGE、CLIP 或自建模型服务。
 */
public interface EmbeddingProvider {
    List<Double> embed(EmbeddingRequest request);
}
