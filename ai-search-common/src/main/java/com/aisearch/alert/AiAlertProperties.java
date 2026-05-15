package com.aisearch.alert;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 告警配置，公共配置建议放入 Nacos 的 ai-search-common.yaml。
 */
@Component
@ConfigurationProperties(prefix = "ai-search.alert")
public class AiAlertProperties {
    private boolean enabled;
    private String analyzerProvider = "dashscope";
    private String analyzerApiKey;
    private String analyzerEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private String analyzerModel = "qwen-plus";
    private String dingtalkEndpoint = "https://oapi.dingtalk.com/robot/send";
    private String dingtalkAccessToken;
    private String dingtalkSecret;
    private int deduplicateWindowSeconds = 300;
    private int maxAlertsPerMinute = 5;
    private int analyzerTimeoutSeconds = 10;
    private int notifyTimeoutSeconds = 5;
    private List<String> ignoredExceptions = new ArrayList<>(List.of(
            "org.springframework.web.bind.MethodArgumentNotValidException",
            "jakarta.validation.ConstraintViolationException",
            "java.lang.IllegalArgumentException"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAnalyzerProvider() {
        return analyzerProvider;
    }

    public void setAnalyzerProvider(String analyzerProvider) {
        this.analyzerProvider = analyzerProvider;
    }

    public String getAnalyzerApiKey() {
        return analyzerApiKey;
    }

    public void setAnalyzerApiKey(String analyzerApiKey) {
        this.analyzerApiKey = analyzerApiKey;
    }

    public String getAnalyzerEndpoint() {
        return analyzerEndpoint;
    }

    public void setAnalyzerEndpoint(String analyzerEndpoint) {
        this.analyzerEndpoint = analyzerEndpoint;
    }

    public String getAnalyzerModel() {
        return analyzerModel;
    }

    public void setAnalyzerModel(String analyzerModel) {
        this.analyzerModel = analyzerModel;
    }

    public String getDingtalkEndpoint() {
        return dingtalkEndpoint;
    }

    public void setDingtalkEndpoint(String dingtalkEndpoint) {
        this.dingtalkEndpoint = dingtalkEndpoint;
    }

    public String getDingtalkAccessToken() {
        return dingtalkAccessToken;
    }

    public void setDingtalkAccessToken(String dingtalkAccessToken) {
        this.dingtalkAccessToken = dingtalkAccessToken;
    }

    public String getDingtalkSecret() {
        return dingtalkSecret;
    }

    public void setDingtalkSecret(String dingtalkSecret) {
        this.dingtalkSecret = dingtalkSecret;
    }

    public int getDeduplicateWindowSeconds() {
        return deduplicateWindowSeconds;
    }

    public void setDeduplicateWindowSeconds(int deduplicateWindowSeconds) {
        this.deduplicateWindowSeconds = deduplicateWindowSeconds;
    }

    public int getMaxAlertsPerMinute() {
        return maxAlertsPerMinute;
    }

    public void setMaxAlertsPerMinute(int maxAlertsPerMinute) {
        this.maxAlertsPerMinute = maxAlertsPerMinute;
    }

    public int getAnalyzerTimeoutSeconds() {
        return analyzerTimeoutSeconds;
    }

    public void setAnalyzerTimeoutSeconds(int analyzerTimeoutSeconds) {
        this.analyzerTimeoutSeconds = analyzerTimeoutSeconds;
    }

    public int getNotifyTimeoutSeconds() {
        return notifyTimeoutSeconds;
    }

    public void setNotifyTimeoutSeconds(int notifyTimeoutSeconds) {
        this.notifyTimeoutSeconds = notifyTimeoutSeconds;
    }

    public List<String> getIgnoredExceptions() {
        return ignoredExceptions;
    }

    public void setIgnoredExceptions(List<String> ignoredExceptions) {
        this.ignoredExceptions = ignoredExceptions == null ? List.of() : ignoredExceptions;
    }
}
