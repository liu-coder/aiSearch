package com.aisearch.search.domain;

import com.aisearch.common.search.QueryType;
import com.aisearch.common.search.RecallSource;
import com.aisearch.common.search.SearchPlan;
import java.util.List;
import java.util.Map;

/**
 * 查询理解后的内部意图模型，供召回编排层使用。
 */
public record QueryIntent(
        QueryType queryType,
        SearchIntent intent,
        String text,
        String imageUrl,
        String semanticQuery,
        List<String> keywords,
        Map<String, String> filters,
        List<RecallSource> recallSources,
        Map<RecallSource, Double> sourceWeights
) {
    public SearchPlan toSearchPlan() {
        return new SearchPlan(intent.name(), semanticQuery, filters, recallSources, sourceWeights);
    }
}
