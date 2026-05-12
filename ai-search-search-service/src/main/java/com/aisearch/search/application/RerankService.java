package com.aisearch.search.application;

import com.aisearch.search.domain.CandidateSegment;
import com.aisearch.search.infrastructure.ModelEmbeddingClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 重排服务。优先调用模型精排，模型不可用时降级为规则排序。
 */
@Service
public class RerankService {
    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    private final ModelEmbeddingClient modelEmbeddingClient;

    RerankService() {
        this.modelEmbeddingClient = null;
    }

    public RerankService(ModelEmbeddingClient modelEmbeddingClient) {
        this.modelEmbeddingClient = modelEmbeddingClient;
    }

    public List<CandidateSegment> rank(String query, List<CandidateSegment> candidates, int topK) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        if (modelEmbeddingClient == null) {
            return ruleRank(candidates, topK);
        }
        try {
            return modelRank(query, candidates, topK);
        } catch (Exception ex) {
            log.warn("model_rerank_failed fallback=rule reason={}", ex.getMessage());
            return ruleRank(candidates, topK);
        }
    }

    private List<CandidateSegment> modelRank(String query, List<CandidateSegment> candidates, int topK) {
        /*
         * 模型精排步骤：
         * 1. 把候选片段压缩成“标题 + 时间 + 原始分数 + 召回来源”的可读文档。
         * 2. 调用 model-service 的 DashScope rerank 接口得到候选下标和相关性分。
         * 3. 用模型分数叠加小幅多通道 boost，保留多路召回的可信度信号。
         */
        List<String> documents = candidates.stream().map(this::rerankDocument).toList();
        List<ModelEmbeddingClient.RerankHit> hits = modelEmbeddingClient.rerank(query, documents, topK);
        List<CandidateSegment> ranked = new ArrayList<>();
        for (ModelEmbeddingClient.RerankHit hit : hits) {
            if (hit.index() >= 0 && hit.index() < candidates.size()) {
                CandidateSegment candidate = candidates.get(hit.index());
                ranked.add(candidate.withScore(hit.score() + sourceDiversityBoost(candidate)));
            }
        }
        if (ranked.isEmpty()) {
            return ruleRank(candidates, topK);
        }
        return ranked.stream()
                .sorted(Comparator.comparingDouble(CandidateSegment::score).reversed())
                .limit(topK)
                .toList();
    }

    private List<CandidateSegment> ruleRank(List<CandidateSegment> candidates, int topK) {
        return candidates.stream()
                .map(candidate -> candidate.withScore(candidate.score() + sourceDiversityBoost(candidate)))
                .sorted(Comparator.comparingDouble(CandidateSegment::score).reversed())
                .limit(topK)
                .toList();
    }

    private double sourceDiversityBoost(CandidateSegment candidate) {
        // 多通道同时命中的候选更可信，给一个受控的小幅加权。
        return Math.min(0.12, candidate.recallSources().size() * 0.03);
    }

    private String rerankDocument(CandidateSegment candidate) {
        return "title=" + candidate.title()
                + ", time=" + candidate.startTimeMs() + "-" + candidate.endTimeMs()
                + ", score=" + candidate.score()
                + ", sources=" + candidate.recallSources();
    }
}
