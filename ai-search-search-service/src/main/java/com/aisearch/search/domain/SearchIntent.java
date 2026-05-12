package com.aisearch.search.domain;

/**
 * 查询意图分类，决定召回阶段更偏相似画面、语义主题还是精确实体。
 */
public enum SearchIntent {
    SEMANTIC_VIDEO_SEARCH,
    SIMILAR_IMAGE_SEARCH,
    MIXED_EVIDENCE_SEARCH,
    EXACT_ENTITY_SEARCH
}
