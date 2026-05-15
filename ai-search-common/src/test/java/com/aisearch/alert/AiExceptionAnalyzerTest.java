package com.aisearch.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class AiExceptionAnalyzerTest {

    @Test
    void fallsBackWhenProviderApiKeyIsMissing() {
        AiAlertProperties properties = new AiAlertProperties();
        AiExceptionAnalyzer analyzer = new AiExceptionAnalyzer(RestClient.builder(), properties, new ObjectMapper());

        AiExceptionAnalyzer.AnalysisResult result = analyzer.analyze(
                "ai-search-test",
                new IllegalStateException("下游模型服务不可用"),
                "GET /api/test");

        assertThat(result.severity()).isEqualTo("P1");
        assertThat(result.rootCause()).contains("下游模型服务不可用");
        assertThat(result.impactScope()).contains("未配置 AI 告警分析 API Key");
    }

    @Test
    void analyzesWithDashscopeOpenAiCompatibleChatCompletion() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiAlertProperties properties = new AiAlertProperties();
        properties.setAnalyzerProvider("dashscope");
        properties.setAnalyzerApiKey("dashscope-key");
        properties.setAnalyzerEndpoint("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");
        properties.setAnalyzerModel("qwen-plus");
        server.expect(once(), requestTo(properties.getAnalyzerEndpoint()))
                .andExpect(header("Authorization", "Bearer dashscope-key"))
                .andExpect(jsonPath("$.model").value("qwen-plus"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"rootCause\\":\\"模型服务未启动\\",\\"impactScope\\":\\"搜索接口返回502\\",\\"suggestion\\":\\"恢复model-service并检查健康状态\\",\\"severity\\":\\"P1\\"}"}}]}
                        """, MediaType.APPLICATION_JSON));
        AiExceptionAnalyzer analyzer = new AiExceptionAnalyzer(builder, properties, new ObjectMapper());

        AiExceptionAnalyzer.AnalysisResult result = analyzer.analyze(
                "ai-search-search-service",
                new IllegalStateException("Connection refused"),
                "POST /api/search");

        assertThat(result.rootCause()).isEqualTo("模型服务未启动");
        assertThat(result.impactScope()).isEqualTo("搜索接口返回502");
        assertThat(result.suggestion()).isEqualTo("恢复model-service并检查健康状态");
        assertThat(result.severity()).isEqualTo("P1");
        server.verify();
    }
}
