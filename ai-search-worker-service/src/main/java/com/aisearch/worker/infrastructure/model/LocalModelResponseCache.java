package com.aisearch.worker.infrastructure.model;

import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认进程内 LRU 风格缓存，适合本地开发和单实例部署。
 */
@Component
@ConditionalOnProperty(prefix = "ai-search.worker.model", name = "cache-type", havingValue = "local", matchIfMissing = true)
public class LocalModelResponseCache implements ModelResponseCache {
    private final int maxEntries;
    private final Map<String, Object> cache = new LinkedHashMap<>(16, 0.75f, true);

    public LocalModelResponseCache(WorkerPipelineProperties properties) {
        this.maxEntries = Math.max(0, properties.getModel().getCacheMaxEntries());
    }

    @Override
    public Optional<Object> get(String key) {
        if (maxEntries <= 0) {
            return Optional.empty();
        }
        synchronized (cache) {
            return Optional.ofNullable(cache.get(key));
        }
    }

    @Override
    public void put(String key, Object value) {
        if (maxEntries <= 0 || value == null) {
            return;
        }
        synchronized (cache) {
            cache.put(key, value);
            while (cache.size() > maxEntries) {
                String eldestKey = cache.keySet().iterator().next();
                cache.remove(eldestKey);
            }
        }
    }
}
