package com.aisearch.search.application;

import com.aisearch.common.search.SearchRequest;
import com.aisearch.common.search.SearchResponse;
import java.util.Optional;

/**
 * 搜索响应缓存，用于承接 Redis 等生产缓存实现，降低重复查询的召回和模型成本。
 */
public interface SearchResponseCache {
    Optional<SearchResponse> get(SearchRequest request);

    void put(SearchRequest request, SearchResponse response);
}
