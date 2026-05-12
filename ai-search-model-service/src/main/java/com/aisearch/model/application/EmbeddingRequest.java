package com.aisearch.model.application;

import jakarta.validation.constraints.NotBlank;

/**
 * Embedding 请求，sourceType 用于区分 text/image/video-frame 等不同输入来源。
 */
public record EmbeddingRequest(
        @NotBlank String input,
        String sourceType
) {
    public String normalizedSourceType() {
        return sourceType == null || sourceType.isBlank() ? "text" : sourceType;
    }
}
