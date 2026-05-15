package com.aisearch.alert;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DingTalkNotifierTest {

    @Test
    void sendsWithPlainAccessToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiAlertProperties properties = new AiAlertProperties();
        properties.setDingtalkEndpoint("https://oapi.dingtalk.com/robot/send");
        properties.setDingtalkAccessToken("token-123");
        server.expect(once(), requestTo("https://oapi.dingtalk.com/robot/send?access_token=token-123"))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));

        new DingTalkNotifier(builder, properties).send(
                "ai-search-test",
                new IllegalStateException("boom"),
                "POST /api/test",
                new AiExceptionAnalyzer.AnalysisResult("root", "impact", "fix", "P1"));

        server.verify();
    }

    @Test
    void extractsAccessTokenWhenConfiguredValueIsFullWebhook() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiAlertProperties properties = new AiAlertProperties();
        properties.setDingtalkEndpoint("https://oapi.dingtalk.com/robot/send");
        properties.setDingtalkAccessToken("https://oapi.dingtalk.com/robot/send?access_token=token-456");
        server.expect(once(), requestTo("https://oapi.dingtalk.com/robot/send?access_token=token-456"))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));

        new DingTalkNotifier(builder, properties).send(
                "ai-search-test",
                new IllegalStateException("boom"),
                "POST /api/test",
                new AiExceptionAnalyzer.AnalysisResult("root", "impact", "fix", "P1"));

        server.verify();
    }
}
