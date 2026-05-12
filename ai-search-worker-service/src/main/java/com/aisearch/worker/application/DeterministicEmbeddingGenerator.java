package com.aisearch.worker.application;

import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 内置确定性向量生成器，保证离线链路本地可跑；接真实模型后替换此组件。
 */
@Component
public class DeterministicEmbeddingGenerator {
    private final WorkerPipelineProperties properties;

    public DeterministicEmbeddingGenerator(WorkerPipelineProperties properties) {
        this.properties = properties;
    }

    public List<Double> embed(String text) {
        byte[] digest = sha256(text == null ? "" : text);
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
