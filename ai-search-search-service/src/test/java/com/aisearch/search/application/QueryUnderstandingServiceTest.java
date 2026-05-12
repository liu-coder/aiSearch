package com.aisearch.search.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aisearch.common.search.QueryType;
import com.aisearch.common.search.RecallSource;
import com.aisearch.common.search.SearchRequest;
import com.aisearch.search.domain.SearchIntent;
import org.junit.jupiter.api.Test;

class QueryUnderstandingServiceTest {
    private final QueryUnderstandingService service = new QueryUnderstandingService();

    @Test
    void rewritesQueryAndExtractsMetadataAndTimeFilters() {
        var intent = service.understand(new SearchRequest(
                "找 2026-05-01 到 2026-05-10 tag:发布会 author:Alice 新能源车",
                null,
                10,
                false));

        assertThat(intent.queryType()).isEqualTo(QueryType.TEXT);
        assertThat(intent.intent()).isEqualTo(SearchIntent.EXACT_ENTITY_SEARCH);
        assertThat(intent.semanticQuery()).isEqualTo("新能源车 发布会 Alice");
        assertThat(intent.filters())
                .containsEntry("tag", "发布会")
                .containsEntry("author", "Alice")
                .containsEntry("startDate", "2026-05-01")
                .containsEntry("endDate", "2026-05-10");
        assertThat(intent.recallSources()).contains(RecallSource.METADATA);
    }

    @Test
    void imageQueryKeepsRealMultimodalRecallPlan() {
        var intent = service.understand(new SearchRequest(null, "http://localhost/frame.jpg", 10, false));

        assertThat(intent.queryType()).isEqualTo(QueryType.IMAGE);
        assertThat(intent.intent()).isEqualTo(SearchIntent.SIMILAR_IMAGE_SEARCH);
        assertThat(intent.semanticQuery()).isEqualTo("image:http://localhost/frame.jpg");
        assertThat(intent.recallSources()).containsExactly(RecallSource.IMAGE_VECTOR, RecallSource.SEGMENT_VECTOR);
    }
}
