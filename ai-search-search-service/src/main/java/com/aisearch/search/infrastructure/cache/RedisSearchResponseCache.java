package com.aisearch.search.infrastructure.cache;

import com.aisearch.common.search.SearchRequest;
import com.aisearch.common.search.SearchResponse;
import com.aisearch.search.application.SearchResponseCache;
import com.aisearch.search.infrastructure.config.SearchBackendProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "ai-search.search.cache", name = "enabled", havingValue = "true")
public class RedisSearchResponseCache implements SearchResponseCache {
    private static final Logger log = LoggerFactory.getLogger(RedisSearchResponseCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SearchBackendProperties.Cache properties;

    public RedisSearchResponseCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SearchBackendProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties.getCache();
    }

    @Override
    public Optional<SearchResponse> get(SearchRequest request) {
        try {
            String value = redisTemplate.opsForValue().get(cacheKey(request));
            if (!StringUtils.hasText(value)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, SearchResponse.class));
        } catch (Exception ex) {
            log.warn("Failed to read search response cache", ex);
            return Optional.empty();
        }
    }

    @Override
    public void put(SearchRequest request, SearchResponse response) {
        try {
            redisTemplate.opsForValue().set(
                    cacheKey(request),
                    objectMapper.writeValueAsString(response),
                    Duration.ofSeconds(Math.max(1, properties.getTtlSeconds())));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize search response cache", ex);
        } catch (Exception ex) {
            log.warn("Failed to write search response cache", ex);
        }
    }

    private String cacheKey(SearchRequest request) {
        String normalized = nullSafe(request.text()).trim()
                + "\n" + nullSafe(request.imageUrl()).trim()
                + "\n" + request.normalizedTopK()
                + "\n" + request.needsAnalysis();
        return properties.getKeyPrefix() + sha256(normalized);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
