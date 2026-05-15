package com.aisearch.search.infrastructure.cache;

import com.aisearch.common.search.SearchRequest;
import com.aisearch.common.search.SearchResponse;
import com.aisearch.search.application.SearchResponseCache;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ai-search.search.cache", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpSearchResponseCache implements SearchResponseCache {
    @Override
    public Optional<SearchResponse> get(SearchRequest request) {
        return Optional.empty();
    }

    @Override
    public void put(SearchRequest request, SearchResponse response) {
        // 默认不缓存，生产环境可通过 Redis 实现启用。
    }
}
