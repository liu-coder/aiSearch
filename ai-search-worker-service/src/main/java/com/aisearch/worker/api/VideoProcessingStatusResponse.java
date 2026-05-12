package com.aisearch.worker.api;

import java.util.List;
import java.util.Map;

/**
 * 视频离线处理状态响应，聚合阶段、片段和片段产物。
 */
public record VideoProcessingStatusResponse(
        String videoId,
        List<StageTaskView> stages,
        List<SegmentView> segments,
        Map<String, List<String>> segmentArtifacts
) {
}
