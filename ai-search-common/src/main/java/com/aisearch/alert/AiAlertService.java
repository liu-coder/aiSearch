package com.aisearch.alert;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AiAlertService {
    private static final Logger log = LoggerFactory.getLogger(AiAlertService.class);

    private final AiAlertProperties properties;
    private final AlertDeduplicator deduplicator;
    private final AiExceptionAnalyzer analyzer;
    private final DingTalkNotifier notifier;

    public AiAlertService(
            AiAlertProperties properties,
            AlertDeduplicator deduplicator,
            AiExceptionAnalyzer analyzer,
            DingTalkNotifier notifier) {
        this.properties = properties;
        this.deduplicator = deduplicator;
        this.analyzer = analyzer;
        this.notifier = notifier;
    }

    @Async("alertExecutor")
    public void triggerAsync(String serviceName, Throwable ex, String requestContext) {
        if (!properties.isEnabled() || shouldIgnore(ex)) {
            return;
        }
        try {
            String actualServiceName = serviceName == null || serviceName.isBlank() ? "unknown-service" : serviceName;
            String fingerprint = AlertDeduplicator.fingerprint(actualServiceName, ex);
            if (!deduplicator.shouldAlert(actualServiceName, fingerprint)) {
                log.debug("[AiAlert] 告警去重或限流跳过 service={} fingerprint={}", actualServiceName, fingerprint);
                return;
            }
            AiExceptionAnalyzer.AnalysisResult analysis = analyzer.analyze(actualServiceName, ex, requestContext);
            log.info("[AiAlert] AI 分析完成 service={} provider={} severity={}",
                    actualServiceName, properties.getAnalyzerProvider(), analysis.severity());
            notifier.send(actualServiceName, ex, requestContext, analysis);
        } catch (RuntimeException alertFailure) {
            log.warn("[AiAlert] 告警处理失败，已忽略以避免影响业务", alertFailure);
        }
    }

    private boolean shouldIgnore(Throwable ex) {
        List<String> ignored = properties.getIgnoredExceptions();
        return ignored.stream().anyMatch(className -> className.equals(ex.getClass().getName()));
    }
}
