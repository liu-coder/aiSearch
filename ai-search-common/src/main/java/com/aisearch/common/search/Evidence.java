package com.aisearch.common.search;

/**
 * 单条搜索结果的命中证据，LLM 分析必须基于这些证据生成结论。
 */
public record Evidence(
        String source,
        String description,
        double confidence
) {
}
