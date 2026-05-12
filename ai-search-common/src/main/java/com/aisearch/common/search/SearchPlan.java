package com.aisearch.common.search;

import java.util.List;
import java.util.Map;

/**
 * 查询入口生成的执行计划，便于调用方和调试人员理解系统选择了哪些召回通道。
 */
public record SearchPlan(
        String intent,
        String semanticQuery,
        Map<String, String> filters,
        List<RecallSource> recallSources,
        Map<RecallSource, Double> sourceWeights
) {
}
