package com.aisearch.worker.application;

import java.util.List;

/**
 * 视频媒体处理计划，包含估算时长和动态切片结果。
 */
public record MediaProcessingPlan(
        long durationMs,
        long segmentDurationMs,
        String strategyName,
        List<VideoSegmentPlan> segments
) {
}
