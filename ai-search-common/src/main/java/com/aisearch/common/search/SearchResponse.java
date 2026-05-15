package com.aisearch.common.search;

import java.time.Instant;
import java.util.List;

/**
 * 搜索响应，包含结果、查询类型、分析结果和本次请求耗时。
 */
public record SearchResponse(
        String requestId,
        QueryType queryType,
        SearchPlan searchPlan,
        List<SearchResultItem> results,
        DialecticalAnalysis analysis,
        long latencyMs,
        Instant generatedAt
) {
    public SearchResponse withRequestMetadata(String newRequestId, long newLatencyMs, Instant newGeneratedAt) {
        return new SearchResponse(
                newRequestId,
                queryType,
                searchPlan,
                results,
                analysis,
                newLatencyMs,
                newGeneratedAt);
    }
}
