package com.aisearch.worker.application;

import com.aisearch.worker.domain.VideoSegmentEntity;

/**
 * 单个视频片段的离线处理计划，后续索引以 segmentId 作为最小检索粒度。
 */
public record VideoSegmentPlan(
        String segmentId,
        long startTimeMs,
        long endTimeMs,
        long keyFrameTimeMs
) implements VideoSegmentEntity.VideoSegmentPlanLike {
}
