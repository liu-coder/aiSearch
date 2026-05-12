package com.aisearch.search.application;

import com.aisearch.common.search.DialecticalAnalysis;
import com.aisearch.common.search.Evidence;
import com.aisearch.common.search.SearchResultItem;
import com.aisearch.search.domain.QueryIntent;
import com.aisearch.search.infrastructure.ModelEmbeddingClient;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * LLM 分析服务边界。优先调用模型网关生成辩证分析，失败时使用证据约束的规则降级。
 */
@Service
public class LlmAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(LlmAnalysisService.class);

    private final ModelEmbeddingClient modelEmbeddingClient;

    LlmAnalysisService() {
        this.modelEmbeddingClient = null;
    }

    public LlmAnalysisService(ModelEmbeddingClient modelEmbeddingClient) {
        this.modelEmbeddingClient = modelEmbeddingClient;
    }

    public DialecticalAnalysis analyze(QueryIntent intent, List<SearchResultItem> results) {
        if (results.isEmpty()) {
            return new DialecticalAnalysis(
                    List.of(),
                List.of("当前召回结果为空，无法形成正向证据。"),
                List.of("需要检查索引是否已构建、查询是否过窄、模型向量是否可用。"),
                "暂不推荐任何视频。");
        }
        if (modelEmbeddingClient == null) {
            return ruleAnalysis(intent, results);
        }
        try {
            String conclusion = modelEmbeddingClient.analyze(intent.semanticQuery(), evidenceTexts(results));
            return new DialecticalAnalysis(
                    positiveEvidence(results),
                    List.of("模型分析仅基于当前召回证据，未命中的视频不会进入结论。"),
                    List.of("召回结果数量、ASR/OCR 质量和关键帧覆盖度会影响分析充分性。"),
                    conclusion);
        } catch (Exception ex) {
            log.warn("model_analysis_failed fallback=rule reason={}", ex.getMessage());
            return ruleAnalysis(intent, results);
        }
    }

    private DialecticalAnalysis ruleAnalysis(QueryIntent intent, List<SearchResultItem> results) {
        // 降级分析仍然只基于 evidence 和召回来源，保持“不脱离证据生成结论”的约束。
        SearchResultItem first = results.get(0);
        return new DialecticalAnalysis(
                List.of("Top 结果来自 " + first.recallSources().size() + " 个召回通道，片段级分数为 " + first.score()),
                List.of("当前分析基于结构化证据生成，尚未接入真实大模型复核。"),
                List.of("模型向量、字幕、OCR 的真实索引质量会影响最终判断。"),
                "建议优先查看 “" + first.title() + "” 的命中片段，再结合证据判断是否满足查询意图：" + intent.queryType());
    }

    private List<String> evidenceTexts(List<SearchResultItem> results) {
        /*
         * 证据构造步骤：
         * 1. 将每个结果的标题、时间段、分数和证据列表整理为短文本。
         * 2. 控制最多传入 8 条，降低模型调用成本和上下文噪声。
         * 3. 分析模型只接收已召回证据，避免凭空扩展结论。
         */
        return results.stream()
                .limit(8)
                .map(result -> result.title()
                        + " [" + result.startTimeMs() + "-" + result.endTimeMs() + "]"
                        + " score=" + result.score()
                        + " evidence=" + result.evidence().stream().map(Evidence::description).toList())
                .toList();
    }

    private List<String> positiveEvidence(List<SearchResultItem> results) {
        List<String> evidence = new ArrayList<>();
        for (SearchResultItem result : results.stream().limit(3).toList()) {
            evidence.add("命中片段 “" + result.title() + "”，分数 " + result.score() + "，来源 " + result.recallSources());
        }
        return evidence;
    }
}
