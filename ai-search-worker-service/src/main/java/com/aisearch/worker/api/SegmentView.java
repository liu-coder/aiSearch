package com.aisearch.worker.api;

/**
 * 片段调试视图，用于确认内容感知切片结果。
 */
public record SegmentView(
        String segmentId,
        long startTimeMs,
        long endTimeMs
) {
}
