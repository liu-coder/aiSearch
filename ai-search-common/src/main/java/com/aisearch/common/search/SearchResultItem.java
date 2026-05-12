package com.aisearch.common.search;

import java.util.List;

/**
 * 搜索结果项，结果粒度是视频片段，而不是整个视频。
 */
public record SearchResultItem(
        String videoId,
        String segmentId,
        String title,
        long startTimeMs,
        long endTimeMs,
        double score,
        List<RecallSource> recallSources,
        List<Evidence> evidence
) {
}
