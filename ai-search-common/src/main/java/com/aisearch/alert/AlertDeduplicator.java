package com.aisearch.alert;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlertDeduplicator {
    private static final Logger log = LoggerFactory.getLogger(AlertDeduplicator.class);

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final AiAlertProperties properties;
    private final Map<String, Instant> localDedupe = new ConcurrentHashMap<>();
    private final Map<String, RateWindow> localRate = new ConcurrentHashMap<>();

    public AlertDeduplicator(ObjectProvider<StringRedisTemplate> redisTemplateProvider, AiAlertProperties properties) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.properties = properties;
    }

    public boolean shouldAlert(String serviceName, String fingerprint) {
        Boolean redisDecision = tryRedisDeduplicateAndRateLimit(serviceName, fingerprint);
        if (redisDecision != null) {
            return redisDecision;
        }
        return shouldAlertLocally(serviceName, fingerprint);
    }

    private Boolean tryRedisDeduplicateAndRateLimit(String serviceName, String fingerprint) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return null;
        }
        try {
            String dedupeKey = "ai-search:alert:dedupe:" + fingerprint;
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(dedupeKey, "1", Duration.ofSeconds(properties.getDeduplicateWindowSeconds()));
            if (Boolean.FALSE.equals(isNew)) {
                return false;
            }
            String rateKey = "ai-search:alert:rate:" + serviceName;
            Long count = redisTemplate.opsForValue().increment(rateKey);
            if (count != null && count == 1) {
                redisTemplate.expire(rateKey, Duration.ofMinutes(1));
            }
            return count == null || count <= properties.getMaxAlertsPerMinute();
        } catch (RuntimeException ex) {
            log.warn("[AiAlert] Redis 去重限流失败，降级到本地窗口", ex);
            return null;
        }
    }

    private boolean shouldAlertLocally(String serviceName, String fingerprint) {
        Instant now = Instant.now();
        Instant expiresAt = localDedupe.get(fingerprint);
        if (expiresAt != null && expiresAt.isAfter(now)) {
            return false;
        }
        localDedupe.put(fingerprint, now.plusSeconds(properties.getDeduplicateWindowSeconds()));
        cleanupExpired(now);
        RateWindow window = localRate.compute(serviceName, (key, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new RateWindow(now.plusSeconds(60), 1);
            }
            return new RateWindow(current.expiresAt(), current.count() + 1);
        });
        return window.count() <= properties.getMaxAlertsPerMinute();
    }

    private void cleanupExpired(Instant now) {
        if (localDedupe.size() < 1024) {
            return;
        }
        localDedupe.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    public static String fingerprint(String serviceName, Throwable ex) {
        StringBuilder raw = new StringBuilder(serviceName).append(':').append(ex.getClass().getName()).append(':');
        StackTraceElement[] stack = ex.getStackTrace();
        for (int i = 0; i < Math.min(3, stack.length); i++) {
            raw.append(stack[i].getClassName())
                    .append('.')
                    .append(stack[i].getMethodName())
                    .append(':')
                    .append(stack[i].getLineNumber())
                    .append('|');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.toString().getBytes(StandardCharsets.UTF_8)), 0, 12);
        } catch (NoSuchAlgorithmException ex2) {
            return Integer.toHexString(raw.toString().hashCode());
        }
    }

    private record RateWindow(Instant expiresAt, int count) {
    }
}
