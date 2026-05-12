package com.aisearch.model.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 精排请求，documents 为召回阶段候选文本。
 */
public record RerankRequest(
        @NotBlank String query,
        @NotEmpty List<String> documents,
        Integer topN
) {
    public int normalizedTopN() {
        return topN == null || topN <= 0 ? documents.size() : Math.min(topN, documents.size());
    }
}
