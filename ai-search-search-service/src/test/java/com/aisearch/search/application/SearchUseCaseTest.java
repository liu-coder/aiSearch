package com.aisearch.search.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aisearch.common.search.SearchRequest;
import com.aisearch.common.search.SearchResponse;
import com.aisearch.search.infrastructure.HybridRecallOrchestrator;
import com.aisearch.search.infrastructure.LocalFixtureRecallAdapter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                new LlmAnalysisService(),
                new InMemorySearchResponseCache());

        var response = useCase.search(new SearchRequest("新能源车 发布会", null, 5, true));

        assertThat(response.results()).isNotEmpty();
        assertThat(response.searchPlan().recallSources()).contains(com.aisearch.common.search.RecallSource.KEYWORD);
        assertThat(response.results().get(0).evidence()).isNotEmpty();
        assertThat(response.analysis().positiveEvidence()).isNotEmpty();
    }

    @Test
    void repeatedSearchUsesResponseCacheWithFreshRequestMetadata() {
        InMemorySearchResponseCache cache = new InMemorySearchResponseCache();
        SearchUseCase useCase = new SearchUseCase(
                new QueryUnderstandingService(),
                new HybridRecallOrchestrator(List.of(new LocalFixtureRecallAdapter())),
                new CandidateMergeService(),
                new RerankService(),
                new EvidenceService(),
                new LlmAnalysisService(),
                cache);
        SearchRequest request = new SearchRequest("新能源车 发布会", null, 5, false);

        SearchResponse first = useCase.search(request);
        SearchResponse second = useCase.search(request);

        assertThat(cache.putCount).isEqualTo(1);
        assertThat(cache.hitCount).isEqualTo(1);
        assertThat(second.requestId()).isNotEqualTo(first.requestId());
        assertThat(second.results()).isEqualTo(first.results());
    }

    private static class InMemorySearchResponseCache implements SearchResponseCache {
        private final Map<String, SearchResponse> cache = new HashMap<>();
        private int putCount;
        private int hitCount;

        @Override
        public Optional<SearchResponse> get(SearchRequest request) {
            Optional<SearchResponse> response = Optional.ofNullable(cache.get(key(request)));
            response.ifPresent(ignored -> hitCount++);
            return response;
        }

        @Override
        public void put(SearchRequest request, SearchResponse response) {
            putCount++;
            cache.put(key(request), response);
        }

        private String key(SearchRequest request) {
            return request.text() + "|" + request.imageUrl() + "|" + request.normalizedTopK() + "|"
                    + request.needsAnalysis();
        }
    }
}
