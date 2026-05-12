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
 * 验证 Milvus 适配器会先调用模型服务生成向量，再调用 Milvus REST 搜索接口。
 */
class MilvusRecallAdapterTest {
    @Test
    void recallsCandidatesFromMilvusSearch() {
        RestClient.Builder modelBuilder = RestClient.builder().baseUrl("http://localhost:18084");
        MockRestServiceServer modelServer = MockRestServiceServer.bindTo(modelBuilder).build();
        modelServer.expect(requestTo("http://localhost:18084/api/models/embedding?text=%E6%96%B0%E8%83%BD%E6%BA%90"))
                .andRespond(withSuccess("""
                        {"success": true, "data": [0.1, 0.2, 0.3], "message": "OK"}
                        """, MediaType.APPLICATION_JSON));
        ModelEmbeddingClient embeddingClient = new ModelEmbeddingClient(modelBuilder.build());

        RestClient.Builder milvusBuilder = RestClient.builder().baseUrl("http://localhost:19530");
        MockRestServiceServer milvusServer = MockRestServiceServer.bindTo(milvusBuilder).build();
        MilvusRecallAdapter adapter = new MilvusRecallAdapter(
                RecallSource.TEXT_VECTOR,
                milvusBuilder.build(),
                new SearchBackendProperties(),
                embeddingClient);
        milvusServer.expect(requestTo("http://localhost:19530/v2/vectordb/entities/search"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"collectionName\"")))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": [
                            {
                              "distance": 0.91,
                              "videoId": "video-2",
                              "segmentId": "seg-2",
                              "title": "城市道路素材",
                              "startTimeMs": 12000,
                              "endTimeMs": 27000
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var results = adapter.recall(intent(), 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).segmentId()).isEqualTo("seg-2");
        assertThat(results.get(0).recallSources()).containsExactly(RecallSource.TEXT_VECTOR);
        modelServer.verify();
        milvusServer.verify();
    }

    private QueryIntent intent() {
        return new QueryIntent(
                QueryType.TEXT,
                SearchIntent.SEMANTIC_VIDEO_SEARCH,
                "新能源",
                null,
                "新能源",
                List.of("新能源"),
                Map.of(),
                List.of(RecallSource.TEXT_VECTOR),
                Map.of(RecallSource.TEXT_VECTOR, 1.0));
    }
}
