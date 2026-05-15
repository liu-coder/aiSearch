package com.aisearch.worker.infrastructure.model;

import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ModelGatewayClientCacheTest {
    @Test
    void reusesCachedModelResponseForSameRequestBody() {
        WorkerPipelineProperties properties = new WorkerPipelineProperties();
        properties.getModel().setQpsLimit(0);
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:18084");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:18084/api/models/embeddings"))
                .andRespond(withSuccess("""
                        {"success": true, "data": [0.1, 0.2], "message": "OK"}
                        """, MediaType.APPLICATION_JSON));
        ModelGatewayClient client = new ModelGatewayClient(
                builder,
                properties,
                new SimpleMeterRegistry(),
                new LocalModelResponseCache(properties));

        List<Double> first = client.embedding("same segment");
        List<Double> second = client.embedding("same segment");

        assertThat(first).containsExactly(0.1, 0.2);
        assertThat(second).containsExactly(0.1, 0.2);
        server.verify();
    }
}
