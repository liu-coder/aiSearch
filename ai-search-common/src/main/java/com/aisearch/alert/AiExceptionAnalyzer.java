package com.aisearch.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiExceptionAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(AiExceptionAnalyzer.class);

    private final RestClient.Builder builder;
    private final AiAlertProperties properties;
    private final ObjectMapper objectMapper;

    public AiExceptionAnalyzer(RestClient.Builder builder, AiAlertProperties properties, ObjectMapper objectMapper) {
        this.builder = builder;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AnalysisResult analyze(String serviceName, Throwable ex, String requestContext) {
        if (isBlank(properties.getAnalyzerApiKey())) {
            return fallback(ex, "未配置 AI 告警分析 API Key");
        }
        try {
            String response = callOpenAiCompatibleChat(buildPrompt(serviceName, ex, requestContext));
            return parseResponse(response);
        } catch (RuntimeException parseOrHttpFailure) {
            log.warn("[AiAlert] AI 异常分析失败，使用降级结果", parseOrHttpFailure);
            return fallback(ex, "AI 分析暂不可用");
        }
    }

    private String buildPrompt(String serviceName, Throwable ex, String requestContext) {
        String stackTrace = Arrays.stream(ex.getStackTrace())
                .limit(15)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining("\n"));
        return """
                你是 Spring Cloud Alibaba 项目的运维专家，请快速分析以下线上异常。

                服务名：%s
                异常类型：%s
                异常消息：%s
                请求上下文：%s

                堆栈（前15帧）：
                %s

                请只输出 JSON 对象：
                {
                  "rootCause": "一句话说清楚根本原因，25字以内",
                  "impactScope": "影响范围，说明接口、功能或数据",
                  "suggestion": "最直接的修复建议，50字以内",
                  "severity": "P0 或 P1 或 P2"
                }
                """.formatted(
                serviceName,
                ex.getClass().getName(),
                ex.getMessage(),
                isBlank(requestContext) ? "无" : requestContext,
                stackTrace);
    }

    @SuppressWarnings("unchecked")
    private String callOpenAiCompatibleChat(String prompt) {
        Map<String, Object> response = builder.build()
                .post()
                .uri(requiredAnalyzerEndpoint())
                .headers(headers -> headers.setBearerAuth(properties.getAnalyzerApiKey()))
                .body(Map.of(
                        "model", requiredAnalyzerModel(),
                        "messages", List.of(Map.of("role", "user", "content", prompt))))
                .retrieve()
                .body(Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getOrDefault("choices", List.of());
        if (choices.isEmpty()) {
            throw new IllegalStateException("AI 告警分析返回缺少 choices");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).getOrDefault("message", Map.of());
        Object content = message.get("content");
        if (content == null) {
            throw new IllegalStateException("AI 告警分析返回缺少 message.content");
        }
        return content.toString();
    }

    private AnalysisResult parseResponse(String text) {
        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}') + 1;
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("AI 分析返回不是 JSON: " + text);
            }
            JsonNode node = objectMapper.readTree(text.substring(start, end));
            return new AnalysisResult(
                    node.path("rootCause").asText("未知根因"),
                    node.path("impactScope").asText("影响范围待确认"),
                    node.path("suggestion").asText("请查看异常堆栈并人工确认"),
                    normalizeSeverity(node.path("severity").asText("P1")));
        } catch (Exception ex) {
            throw new IllegalArgumentException("AI 分析结果解析失败", ex);
        }
    }

    private AnalysisResult fallback(Throwable ex, String reason) {
        String message = ex.getMessage();
        String rootCause = isBlank(message) ? ex.getClass().getSimpleName() : truncate(message, 80);
        return new AnalysisResult(rootCause, reason, "请结合 traceId 和堆栈定位异常来源", "P1");
    }

    private String normalizeSeverity(String severity) {
        if ("P0".equalsIgnoreCase(severity) || "P1".equalsIgnoreCase(severity) || "P2".equalsIgnoreCase(severity)) {
            return severity.toUpperCase();
        }
        return "P1";
    }

    private String requiredAnalyzerEndpoint() {
        if (!isBlank(properties.getAnalyzerEndpoint())) {
            return properties.getAnalyzerEndpoint();
        }
        return "deepseek".equalsIgnoreCase(properties.getAnalyzerProvider())
                ? "https://api.deepseek.com/chat/completions"
                : "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    }

    private String requiredAnalyzerModel() {
        if (!isBlank(properties.getAnalyzerModel())) {
            return properties.getAnalyzerModel();
        }
        return "deepseek".equalsIgnoreCase(properties.getAnalyzerProvider()) ? "deepseek-chat" : "qwen-plus";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record AnalysisResult(String rootCause, String impactScope, String suggestion, String severity) {
    }
}
