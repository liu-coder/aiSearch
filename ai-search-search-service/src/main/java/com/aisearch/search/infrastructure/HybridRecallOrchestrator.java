package com.aisearch.search.infrastructure;

import com.aisearch.common.search.RecallSource;
import com.aisearch.search.application.RecallAdapter;
import com.aisearch.search.application.RecallOrchestrator;
import com.aisearch.search.domain.CandidateSegment;
import com.aisearch.search.domain.QueryIntent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 混合召回编排器，按查询理解产出的召回计划并行聚合多类候选。
 */
@Component
public class HybridRecallOrchestrator implements RecallOrchestrator {
    private final Map<RecallSource, RecallAdapter> adapters;

    public HybridRecallOrchestrator(List<RecallAdapter> adapters) {
        this.adapters = new EnumMap<>(RecallSource.class);
        for (RecallAdapter adapter : adapters) {
            this.adapters.put(adapter.source(), adapter);
        }
    }

    @Override
    public List<CandidateSegment> recall(QueryIntent intent, int limit) {
        /*
         * 多路召回流程：
         * 1. 遍历查询理解阶段生成的 recallSources，保持召回计划可观测。
         * 2. 每个 source 查找对应 RecallAdapter，未配置时先记录缺失来源。
         * 3. 已配置的适配器各自访问 ES/Milvus/元数据等后端并返回统一候选。
         * 4. 如果没有任何候选且存在缺失适配器，明确失败，避免生产环境静默返回假结果。
         * 5. 最后做一次总量截断，后续 CandidateMergeService 负责片段级去重融合。
         */
        List<CandidateSegment> candidates = new ArrayList<>();
        List<RecallSource> missingSources = new ArrayList<>();
        for (RecallSource source : intent.recallSources()) {
            RecallAdapter adapter = adapters.get(source);
            if (adapter == null) {
                missingSources.add(source);
                continue;
            }
            double weight = intent.sourceWeights().getOrDefault(source, 1.0);
            candidates.addAll(adapter.recall(intent, limit).stream()
                    .map(candidate -> candidate.withScore(candidate.score() * weight))
                    .toList());
        }
        if (candidates.isEmpty() && !missingSources.isEmpty()) {
            throw new IllegalStateException("未配置可用召回适配器: " + missingSources);
        }
        return candidates.stream().limit(limit).toList();
    }
}
