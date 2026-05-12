package com.aisearch.worker.domain;

/**
 * 阶段失败类型，用于区分重试策略和排障入口。
 */
public enum StageFailureType {
    STORAGE,
    MEDIA,
    MODEL_RATE_LIMIT,
    MODEL_TIMEOUT,
    MODEL_RESPONSE,
    SEARCH_INDEX,
    CONFIGURATION,
    UNKNOWN
}
