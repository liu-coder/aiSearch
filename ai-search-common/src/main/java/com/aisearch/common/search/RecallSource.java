package com.aisearch.common.search;

/**
 * 候选视频片段的召回来源，结果融合和证据构建会保留这些来源。
 */
public enum RecallSource {
    KEYWORD,
    TEXT_VECTOR,
    IMAGE_VECTOR,
    SEGMENT_VECTOR,
    OCR,
    ASR,
    METADATA
}
