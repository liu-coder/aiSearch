package com.aisearch.worker.application;

/**
 * 关键帧对象存储产物，绑定对应的视频片段。
 */
public record FrameMediaArtifact(
        VideoSegmentPlan segment,
        String bucket,
        String objectKey,
        String url
) {
}
