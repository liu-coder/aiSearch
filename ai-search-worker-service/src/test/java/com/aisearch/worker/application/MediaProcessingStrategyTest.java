package com.aisearch.worker.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aisearch.worker.domain.VideoAssetReadEntity;
import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 动态媒体策略测试，确保不同视频规模会得到不同切片和关键帧计划。
 */
class MediaProcessingStrategyTest {
    @Test
    void shortVideoUsesDenseSegments() {
        MediaProcessingStrategy strategy = new MediaProcessingStrategy(new WorkerPipelineProperties());

        MediaProcessingPlan plan = strategy.plan(asset(10_000_000L), 60_000L);

        assertThat(plan.strategyName()).isEqualTo("SHORT_VIDEO_DENSE");
        assertThat(plan.segmentDurationMs()).isEqualTo(15_000L);
        assertThat(plan.segments()).hasSize(4);
        assertThat(plan.segments().get(0).keyFrameTimeMs()).isEqualTo(7_500L);
    }

    @Test
    void longLargeVideoUsesSparseLimitedSegments() {
        WorkerPipelineProperties properties = new WorkerPipelineProperties();
        MediaProcessingStrategy strategy = new MediaProcessingStrategy(properties);

        MediaProcessingPlan plan = strategy.plan(asset(900_000_000L), 3_600_000L);

        assertThat(plan.strategyName()).isEqualTo("LONG_VIDEO_SPARSE_LARGE_FILE_LIMITED");
        assertThat(plan.segmentDurationMs()).isEqualTo(120_000L);
        assertThat(plan.segments()).hasSize(properties.getMediaStrategy().getLargeFileMaxFrames());
    }

    @Test
    void absoluteMaxSegmentsCapsAllStrategies() {
        WorkerPipelineProperties properties = new WorkerPipelineProperties();
        properties.getMediaStrategy().setMaxSegments(3);
        MediaProcessingStrategy strategy = new MediaProcessingStrategy(properties);

        MediaProcessingPlan fixedPlan = strategy.plan(asset(10_000_000L), 600_000L);
        MediaProcessingPlan contentPlan = strategy.plan(asset(10_000_000L), 600_000L,
                List.of(30_000L, 60_000L, 90_000L, 120_000L, 150_000L, 180_000L));

        assertThat(fixedPlan.segments()).hasSize(3);
        assertThat(contentPlan.segments()).hasSize(3);
        assertThat(fixedPlan.strategyName()).contains("SAMPLED");
        assertThat(contentPlan.strategyName()).contains("SAMPLED");
    }

    @Test
    void contentBoundariesTakePriorityOverFixedDurationStrategy() {
        MediaProcessingStrategy strategy = new MediaProcessingStrategy(new WorkerPipelineProperties());

        MediaProcessingPlan plan = strategy.plan(asset(30_000_000L), 120_000L, List.of(12_000L, 38_000L, 70_000L));

        assertThat(plan.strategyName()).isEqualTo("CONTENT_AWARE");
        assertThat(plan.segments()).extracting(VideoSegmentPlan::startTimeMs)
                .containsExactly(0L, 12_000L, 38_000L, 70_000L);
        assertThat(plan.segments()).extracting(VideoSegmentPlan::endTimeMs)
                .containsExactly(12_000L, 38_000L, 70_000L, 120_000L);
    }

    @Test
    void oversizedContentSegmentIsSplitByConfiguredMaximum() {
        WorkerPipelineProperties properties = new WorkerPipelineProperties();
        properties.getMediaStrategy().setMaxContentSegmentSeconds(30);
        MediaProcessingStrategy strategy = new MediaProcessingStrategy(properties);

        MediaProcessingPlan plan = strategy.plan(asset(30_000_000L), 100_000L, List.of());

        assertThat(plan.strategyName()).isEqualTo("SHORT_VIDEO_DENSE");

        MediaProcessingPlan contentPlan = strategy.plan(asset(30_000_000L), 100_000L, List.of(95_000L));

        assertThat(contentPlan.strategyName()).isEqualTo("CONTENT_AWARE");
        assertThat(contentPlan.segments()).hasSizeGreaterThan(2);
        assertThat(contentPlan.segments()).allSatisfy(segment ->
                assertThat(segment.endTimeMs() - segment.startTimeMs()).isLessThanOrEqualTo(30_000L));
    }

    private VideoAssetReadEntity asset(long fileSize) {
        try {
            var constructor = VideoAssetReadEntity.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            VideoAssetReadEntity entity = constructor.newInstance();
            ReflectionTestUtils.setField(entity, "videoId", "video-1");
            ReflectionTestUtils.setField(entity, "fileName", "demo.mp4");
            ReflectionTestUtils.setField(entity, "fileSize", fileSize);
            return entity;
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("构造测试视频资产失败", ex);
        }
    }
}
