package com.aisearch.model.infrastructure;

import com.aisearch.model.application.EmbeddingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证 DashScope provider 使用百炼兼容 embeddings 协议。
 */
class DashScopeEmbeddingProviderTest {
    @Test
    void callsDashScopeCompatibleEmbeddingsApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ModelProviderProperties properties = new ModelProviderProperties();
        properties.getDashscope().setApiKey("test-key");
        properties.getDashscope().setEmbeddingEndpoint("http://dashscope.test/compatible-mode/v1/embeddings");
        DashScopeEmbeddingProvider provider = new DashScopeEmbeddingProvider(builder.build(), properties);

        server.expect(requestTo("http://dashscope.test/compatible-mode/v1/embeddings"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"model\":\"text-embedding-v4\"")))
                .andRespond(withSuccess("""
                        {"data":[{"embedding":[0.1,0.2,0.3]}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.embed(new EmbeddingRequest("新能源车发布会", "text"))).containsExactly(0.1, 0.2, 0.3);
        server.verify();
    }
}
