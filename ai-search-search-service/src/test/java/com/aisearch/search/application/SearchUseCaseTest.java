package com.aisearch.search.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aisearch.common.search.SearchRequest;
import com.aisearch.search.infrastructure.HybridRecallOrchestrator;
import com.aisearch.search.infrastructure.LocalFixtureRecallAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 搜索用例的轻量行为测试，确保第一条垂直链路能输出结果、证据和分析。
 */
class SearchUseCaseTest {
    @Test
    void textSearchBuildsRankedResultsWithEvidenceAndAnalysis() {
        SearchUseCase useCase = new SearchUseCase(
                new QueryUnderstandingService(),
                new HybridRecallOrchestrator(List.of(new LocalFixtureRecallAdapter())),
                new CandidateMergeService(),
                new RerankService(),
                new EvidenceService(),
                new LlmAnalysisService());

        var response = useCase.search(new SearchRequest("新能源车 发布会", null, 5, true));

        assertThat(response.results()).isNotEmpty();
        assertThat(response.searchPlan().recallSources()).contains(com.aisearch.common.search.RecallSource.KEYWORD);
        assertThat(response.results().get(0).evidence()).isNotEmpty();
        assertThat(response.analysis().positiveEvidence()).isNotEmpty();
    }
}
