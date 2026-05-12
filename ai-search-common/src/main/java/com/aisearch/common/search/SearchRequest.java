package com.aisearch.common.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * 搜索请求模型，支持文字、图片或文字 + 图片混合检索。
 */
public record SearchRequest(
        @Size(max = 500) String text,
        @Size(max = 2048) String imageUrl,
        @Min(1) @Max(100) Integer topK,
        Boolean withAnalysis
) {
    @AssertTrue(message = "text 或 imageUrl 至少需要提供一个")
    public boolean hasSearchCondition() {
        return hasText(text) || hasText(imageUrl);
    }

    /**
     * 避免调用方不传 topK 时出现空值，默认返回 10 条结果。
     */
    public int normalizedTopK() {
        return topK == null ? 10 : topK;
    }

    /**
     * 默认生成辩证分析；调用方可显式关闭以降低延迟和模型成本。
     */
    public boolean needsAnalysis() {
        return withAnalysis == null || withAnalysis;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
