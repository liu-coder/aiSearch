package com.aisearch.worker.infrastructure.model;

import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 分布式模型响应缓存，适合多 worker 实例共享模型调用结果。
 */
@Component
@ConditionalOnProperty(prefix = "ai-search.worker.model", name = "cache-type", havingValue = "redis")
public class RedisModelResponseCache implements ModelResponseCache {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final Duration ttl;

    public RedisModelResponseCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            WorkerPipelineProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = properties.getModel().getCacheKeyPrefix();
        this.ttl = Duration.ofSeconds(Math.max(1L, properties.getModel().getCacheTtlSeconds()));
    }

    @Override
    public Optional<Object> get(String key) {
        try {
            String payload = redisTemplate.opsForValue().get(keyPrefix + key);
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(payload, Object.class));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, Object value) {
        if (value == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(keyPrefix + key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // 缓存失败不应阻断离线处理主链路。
        }
    }
}
