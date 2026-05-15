package com.aisearch.search.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aisearch.common.search.QueryType;
import com.aisearch.common.search.RecallSource;
import com.aisearch.common.search.SearchRequest;
import com.aisearch.search.infrastructure.ModelEmbeddingClient;
import com.aisearch.search.domain.SearchIntent;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

    @Test
    void parsesStructuredModelUnderstandingJsonAndSourceWeights() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:18084");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:18084/api/models/analysis"))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": "{\\"rewrite\\":\\"王工 车间 巡检异常\\",\\"intent\\":\\"EXACT_ENTITY_SEARCH\\",\\"person\\":\\"王工\\",\\"scene\\":\\"车间\\",\\"sourceWeights\\":{\\"KEYWORD\\":1.3,\\"OCR\\":1.2}}",
                          "message": "OK"
                        }
                        """, MediaType.APPLICATION_JSON));
        QueryUnderstandingService modelService = new QueryUnderstandingService(new ModelEmbeddingClient(builder.build()));

        var intent = modelService.understand(new SearchRequest("帮我找王工在车间巡检异常的视频", null, 10, false));

        assertThat(intent.intent()).isEqualTo(SearchIntent.EXACT_ENTITY_SEARCH);
        assertThat(intent.semanticQuery()).isEqualTo("王工 车间 巡检异常");
        assertThat(intent.filters()).containsEntry("person", "王工").containsEntry("scene", "车间");
        assertThat(intent.sourceWeights()).containsAllEntriesOf(Map.of(
                RecallSource.KEYWORD, 1.3,
                RecallSource.OCR, 1.2));
        server.verify();
    }
}
