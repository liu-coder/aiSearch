package com.aisearch.search.application;

import com.aisearch.common.search.DialecticalAnalysis;
import com.aisearch.common.search.SearchRequest;
import com.aisearch.common.search.SearchResponse;
import com.aisearch.common.search.SearchResultItem;
import com.aisearch.search.domain.CandidateSegment;
import com.aisearch.search.domain.QueryIntent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 在线搜索主用例，串联查询理解、召回、融合、重排、证据构建和分析。
 */
@Service
public class SearchUseCase {
    private final QueryUnderstandingService queryUnderstandingService;
    private final RecallOrchestrator recallOrchestrator;
    private final CandidateMergeService candidateMergeService;
    private final RerankService rerankService;
    private final EvidenceService evidenceService;
    private final LlmAnalysisService llmAnalysisService;

    public SearchUseCase(
            QueryUnderstandingService queryUnderstandingService,
            RecallOrchestrator recallOrchestrator,
            CandidateMergeService candidateMergeService,
            RerankService rerankService,
            EvidenceService evidenceService,
            LlmAnalysisService llmAnalysisService) {
        this.queryUnderstandingService = queryUnderstandingService;
        this.recallOrchestrator = recallOrchestrator;
        this.candidateMergeService = candidateMergeService;
        this.rerankService = rerankService;
        this.evidenceService = evidenceService;
        this.llmAnalysisService = llmAnalysisService;
    }

    public SearchResponse search(SearchRequest request) {
        /*
         * 在线检索主流程：
         * 1. 生成 requestId 并记录开始时间，便于后续接入 traceId 和耗时指标。
         * 2. 查询理解：判断 TEXT/IMAGE/MIXED，抽取过滤条件并生成召回计划。
         * 3. 多路召回：按计划调用 ES、Milvus、元数据等适配器。
         * 4. 候选融合和重排：合并同片段多通道命中，再按规则/模型排序。
         * 5. 证据构建和辩证分析：把内部候选转成对外结果，并按需调用分析链路。
         */
        long started = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        // 保持主流程显式编排，便于后续替换真实 ES、Milvus、Redis、LLM 适配器。
        QueryIntent intent = queryUnderstandingService.understand(request);
        List<CandidateSegment> recalled = recallOrchestrator.recall(intent, request.normalizedTopK() * 5);
        List<CandidateSegment> merged = candidateMergeService.merge(recalled);
        List<CandidateSegment> ranked = rerankService.rank(intent.semanticQuery(), merged, request.normalizedTopK());
        List<SearchResultItem> results = evidenceService.build(ranked);
        DialecticalAnalysis analysis = request.needsAnalysis()
                ? llmAnalysisService.analyze(intent, results)
                : DialecticalAnalysis.skipped();
        return new SearchResponse(
                requestId,
                intent.queryType(),
                intent.toSearchPlan(),
                results,
                analysis,
                System.currentTimeMillis() - started,
                Instant.now());
    }
}
