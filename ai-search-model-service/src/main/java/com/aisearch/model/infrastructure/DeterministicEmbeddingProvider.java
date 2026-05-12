package com.aisearch.model.infrastructure;

import com.aisearch.model.application.EmbeddingProvider;
import com.aisearch.model.application.EmbeddingRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地确定性 provider，便于无模型密钥时稳定开发和测试。
 */
public class DeterministicEmbeddingProvider implements EmbeddingProvider {
    private final ModelProviderProperties properties;

    public DeterministicEmbeddingProvider(ModelProviderProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Double> embed(EmbeddingRequest request) {
        byte[] digest = sha256(request.normalizedSourceType() + ":" + request.input());
        List<Double> vector = new ArrayList<>(properties.getEmbeddingDimension());
        for (int i = 0; i < properties.getEmbeddingDimension(); i++) {
            vector.add((digest[i % digest.length] & 0xff) / 255.0);
        }
        return vector;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }
}
