package com.aisearch.search.infrastructure;

import com.aisearch.common.search.QueryType;
import com.aisearch.common.search.RecallSource;
import com.aisearch.search.domain.QueryIntent;
import com.aisearch.search.domain.SearchIntent;
import com.aisearch.search.infrastructure.config.SearchBackendProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证 Elasticsearch 适配器会调用真实 REST 查询接口并映射候选片段。
 */
class ElasticsearchRecallAdapterTest {
    @Test
    void recallsCandidatesFromElasticsearchHits() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:9200");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ElasticsearchRecallAdapter adapter = new ElasticsearchRecallAdapter(
                RecallSource.KEYWORD,
                builder.build(),
                new SearchBackendProperties());
        server.expect(requestTo("http://localhost:9200/ai_video_segments/_search"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"multi_match\"")))
                .andRespond(withSuccess("""
                        {
                          "hits": {
                            "hits": [
                              {
                                "_score": 1.2,
                                "_source": {
                                  "videoId": "video-1",
                                  "segmentId": "seg-1",
                                  "title": "新能源发布会",
                                  "startTimeMs": 1000,
                                  "endTimeMs": 5000
                                }
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var results = adapter.recall(intent(), 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).videoId()).isEqualTo("video-1");
        assertThat(results.get(0).recallSources()).containsExactly(RecallSource.KEYWORD);
        server.verify();
    }

    private QueryIntent intent() {
        return new QueryIntent(
                QueryType.TEXT,
                SearchIntent.SEMANTIC_VIDEO_SEARCH,
                "新能源 发布会",
                null,
                "新能源 发布会",
                List.of("新能源", "发布会"),
                Map.of(),
                List.of(RecallSource.KEYWORD),
                Map.of(RecallSource.KEYWORD, 1.0));
    }
}
