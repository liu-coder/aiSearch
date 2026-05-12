package com.aisearch.model.application;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 辩证分析请求，evidence 为检索命中的证据摘要。
 */
public record AnalysisRequest(
        @NotBlank String query,
        List<String> evidence
) {
}
