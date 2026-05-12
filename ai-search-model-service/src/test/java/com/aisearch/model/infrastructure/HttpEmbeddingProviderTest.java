package com.aisearch.model.infrastructure;

import com.aisearch.model.application.EmbeddingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证 HTTP Embedding provider 能把请求转发给外部模型服务并解析向量。
 */
class HttpEmbeddingProviderTest {
    @Test
    void callsExternalEmbeddingService() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ModelProviderProperties properties = new ModelProviderProperties();
        properties.getHttp().setEndpoint("http://localhost:19090/embedding");
        HttpEmbeddingProvider provider = new HttpEmbeddingProvider(builder.build(), properties);

        server.expect(requestTo("http://localhost:19090/embedding"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"input\":\"hello\"")))
                .andRespond(withSuccess("{\"embedding\":[0.1,0.2]}", MediaType.APPLICATION_JSON));

        assertThat(provider.embed(new EmbeddingRequest("hello", "text"))).containsExactly(0.1, 0.2);
        server.verify();
    }
}
