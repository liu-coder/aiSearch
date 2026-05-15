package com.aisearch.worker.infrastructure.model;

import java.util.Optional;

/**
 * 模型响应缓存端口，支持进程内缓存和 Redis 分布式缓存两种实现。
 */
public interface ModelResponseCache {
    Optional<Object> get(String key);

    void put(String key, Object value);
}
