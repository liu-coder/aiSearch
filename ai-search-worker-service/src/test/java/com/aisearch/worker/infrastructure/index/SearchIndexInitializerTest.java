package com.aisearch.worker.infrastructure.index;

import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.HEAD;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

/**
 * 验证启动初始化器会在索引结构缺失时创建 ES index 和 Milvus collection。
 */
class SearchIndexInitializerTest {
    @Test
    void createsMissingElasticsearchIndexAndMilvusCollection() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WorkerPipelineProperties properties = new WorkerPipelineProperties();
        properties.getElasticsearch().setEndpoint("http://localhost:9200");
        properties.getMilvus().setEndpoint("http://localhost:9200");
        SearchIndexInitializer initializer = new SearchIndexInitializer(builder, properties);

        server.expect(requestTo("http://localhost:9200/ai_video_segments"))
                .andExpect(method(HEAD))
                .andRespond(withResourceNotFound());
        server.expect(requestTo("http://localhost:9200/ai_video_segments"))
                .andExpect(method(PUT))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"mappings\"")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:9200/v2/vectordb/collections/describe"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"code\": 100, \"message\": \"not found\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:9200/v2/vectordb/collections/create"))
                .andExpect(method(POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"collectionName\"")))
                .andRespond(withSuccess("{\"code\": 0}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:9200/v2/vectordb/collections/load"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"code\": 0}", MediaType.APPLICATION_JSON));

        initializer.run(null);

        server.verify();
    }
}
