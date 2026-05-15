package com.aisearch.alert;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DingTalkNotifier {
    private static final Logger log = LoggerFactory.getLogger(DingTalkNotifier.class);
    private static final Map<String, String> SEVERITY_MARK = Map.of(
            "P0", "[P0]",
            "P1", "[P1]",
            "P2", "[P2]");

    private final RestClient.Builder builder;
    private final AiAlertProperties properties;

    public DingTalkNotifier(RestClient.Builder builder, AiAlertProperties properties) {
        this.builder = builder;
        this.properties = properties;
    }

    public void send(String serviceName, Throwable ex, String requestContext, AiExceptionAnalyzer.AnalysisResult analysis) {
        if (isBlank(properties.getDingtalkAccessToken())) {
            log.warn("[AiAlert] 未配置 ai-search.alert.dingtalk-access-token，跳过钉钉推送");
            return;
        }
        try {
            String title = "%s %s 线上异常告警".formatted(analysis.severity(), serviceName);
            Map<?, ?> response = builder.build()
                    .post()
                    .uri(buildWebhookUrl())
                    .body(Map.of(
                            "msgtype", "markdown",
                            "markdown", Map.of("title", title, "text", buildMarkdown(serviceName, ex, requestContext, analysis))))
                    .retrieve()
                    .body(Map.class);
            if (isSuccess(response)) {
                log.info("[AiAlert] 钉钉推送完成 service={} severity={}", serviceName, analysis.severity());
            } else {
                log.warn("[AiAlert] 钉钉返回失败 service={} response={}", serviceName, response);
            }
        } catch (RuntimeException ex2) {
            log.error("[AiAlert] 钉钉推送失败", ex2);
        }
    }

    private boolean isSuccess(Map<?, ?> response) {
        if (response == null) {
            return false;
        }
        Object errcode = response.get("errcode");
        if (errcode instanceof Number number) {
            return number.intValue() == 0;
        }
        return "0".equals(String.valueOf(errcode));
    }

    private String buildMarkdown(
            String serviceName,
            Throwable ex,
            String requestContext,
            AiExceptionAnalyzer.AnalysisResult analysis) {
        String stackTop = ex.getStackTrace().length == 0 ? "无堆栈" : ex.getStackTrace()[0].toString();
        return """
                ## %s %s

                **服务**：`%s`

                **时间**：`%s`

                **上下文**：`%s`

                ---

                **根因**：%s

                **影响范围**：%s

                **修复建议**：%s

                ---

                **异常信息**：`%s: %s`

                **抛出位置**：`%s`
                """.formatted(
                SEVERITY_MARK.getOrDefault(analysis.severity(), "[P1]"),
                serviceName,
                serviceName,
                Instant.now(),
                isBlank(requestContext) ? "无" : requestContext,
                analysis.rootCause(),
                analysis.impactScope(),
                analysis.suggestion(),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                stackTop);
    }

    private String buildWebhookUrl() {
        String url = endpointFromConfiguredToken();
        if (!isBlank(url)) {
            return appendSignIfNecessary(url);
        }
        url = properties.getDingtalkEndpoint() + "?access_token=" + urlEncode(accessTokenFromConfiguredValue());
        return appendSignIfNecessary(url);
    }

    private String appendSignIfNecessary(String url) {
        if (isBlank(properties.getDingtalkSecret())) {
            return url;
        }
        long timestamp = System.currentTimeMillis();
        return url + "&timestamp=" + timestamp + "&sign=" + sign(timestamp);
    }

    private String endpointFromConfiguredToken() {
        String value = properties.getDingtalkAccessToken();
        if (isBlank(value) || !value.startsWith("http")) {
            return null;
        }
        int tokenIndex = value.indexOf("access_token=");
        if (tokenIndex < 0) {
            return value;
        }
        return properties.getDingtalkEndpoint() + "?access_token=" + urlEncode(extractAccessToken(value));
    }

    private String accessTokenFromConfiguredValue() {
        String value = properties.getDingtalkAccessToken();
        String token = extractAccessToken(value);
        return isBlank(token) ? value : token;
    }

    private String extractAccessToken(String value) {
        if (isBlank(value)) {
            return value;
        }
        int index = value.indexOf("access_token=");
        if (index < 0) {
            return value;
        }
        String token = value.substring(index + "access_token=".length());
        int ampersand = token.indexOf('&');
        return ampersand < 0 ? token : token.substring(0, ampersand);
    }

    private String sign(long timestamp) {
        try {
            String stringToSign = timestamp + "\n" + properties.getDingtalkSecret();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getDingtalkSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return urlEncode(Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception ex) {
            throw new IllegalStateException("生成钉钉加签失败", ex);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
