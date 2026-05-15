package com.aisearch.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class AlertDeduplicatorTest {

    @Test
    void localFallbackDeduplicatesSameFingerprint() {
        AiAlertProperties properties = new AiAlertProperties();
        properties.setDeduplicateWindowSeconds(300);
        properties.setMaxAlertsPerMinute(5);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        AlertDeduplicator deduplicator = new AlertDeduplicator(redisProvider, properties);

        assertThat(deduplicator.shouldAlert("ai-search-test", "same-error")).isTrue();
        assertThat(deduplicator.shouldAlert("ai-search-test", "same-error")).isFalse();
    }

    @Test
    void localFallbackLimitsAlertsPerService() {
        AiAlertProperties properties = new AiAlertProperties();
        properties.setDeduplicateWindowSeconds(300);
        properties.setMaxAlertsPerMinute(1);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        AlertDeduplicator deduplicator = new AlertDeduplicator(redisProvider, properties);

        assertThat(deduplicator.shouldAlert("ai-search-test", "first-error")).isTrue();
        assertThat(deduplicator.shouldAlert("ai-search-test", "second-error")).isFalse();
    }
}
