package com.aisearch.worker.application;

import com.aisearch.worker.domain.VideoAssetReadEntity;
import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 动态媒体处理策略，根据视频时长和大小生成片段与关键帧计划。
 */
@Component
public class MediaProcessingStrategy {
    private final WorkerPipelineProperties properties;

    public MediaProcessingStrategy(WorkerPipelineProperties properties) {
        this.properties = properties;
    }

    /**
     * 根据 ffprobe 探测时长和资产大小选择切片策略。
     */
    public MediaProcessingPlan plan(VideoAssetReadEntity asset, long detectedDurationMs) {
        return plan(asset, detectedDurationMs, List.of());
    }

    /**
     * 根据 ffprobe 探测时长和内容边界选择切片策略。
     */
    public MediaProcessingPlan plan(VideoAssetReadEntity asset, long detectedDurationMs, List<Long> contentBoundaryTimesMs) {
        /*
         * 动态策略步骤：
         * 1. 优先使用 ffprobe 探测到的视频时长；探测失败时按配置默认时长兜底。
         * 2. 有镜头变化/静音等内容边界时优先按自然边界切片。
         * 3. 内容边界不可用时，短视频密集切、中长视频逐步放宽切片间隔。
         * 4. 对大文件进一步限制最大关键帧数量，避免离线任务被单个超大视频拖垮。
         * 5. 每个片段取中点作为关键帧时间，保证比固定首秒更接近片段语义中心。
         */
        WorkerPipelineProperties.MediaStrategy strategy = properties.getMediaStrategy();
        long durationMs = detectedDurationMs > 0 ? detectedDurationMs : strategy.getDefaultDurationSeconds() * 1000L;
        StrategyChoice choice = choose(strategy, Math.max(1, durationMs / 1000L), asset.getFileSize());
        if (strategy.isContentAwareEnabled() && contentBoundaryTimesMs != null && !contentBoundaryTimesMs.isEmpty()) {
            List<VideoSegmentPlan> contentSegments = buildContentAwareSegments(asset.getVideoId(), durationMs, contentBoundaryTimesMs, choice.maxFrames());
            if (!contentSegments.isEmpty()) {
                return new MediaProcessingPlan(durationMs, averageDurationMs(contentSegments), choice.contentName(contentSegments.size()), contentSegments);
            }
        }
        long durationSeconds = Math.max(1, durationMs / 1000L);
        List<VideoSegmentPlan> segments = buildSegments(asset.getVideoId(), durationMs, choice.segmentSeconds(), choice.maxFrames());
        return new MediaProcessingPlan(durationMs, choice.segmentSeconds() * 1000L, choice.name(segments.size()), segments);
    }

    private StrategyChoice choose(WorkerPipelineProperties.MediaStrategy strategy, long durationSeconds, long fileSize) {
        String name;
        long segmentSeconds;
        if (durationSeconds <= strategy.getShortVideoSeconds()) {
            name = "SHORT_VIDEO_DENSE";
            segmentSeconds = strategy.getShortSegmentSeconds();
        } else if (durationSeconds <= strategy.getMediumVideoSeconds()) {
            name = "MEDIUM_VIDEO_BALANCED";
            segmentSeconds = strategy.getMediumSegmentSeconds();
        } else {
            name = "LONG_VIDEO_SPARSE";
            segmentSeconds = strategy.getLongSegmentSeconds();
        }
        int maxFrames = Math.min(strategy.getMaxFrames(), strategy.getMaxSegments());
        if (fileSize >= strategy.getLargeFileBytes()) {
            name = name + "_LARGE_FILE_LIMITED";
            maxFrames = Math.min(maxFrames, Math.min(strategy.getLargeFileMaxFrames(), strategy.getMaxSegments()));
        }
        String contentName = fileSize >= strategy.getLargeFileBytes() ? "CONTENT_AWARE_LARGE_FILE_LIMITED" : "CONTENT_AWARE";
        return new StrategyChoice(name, contentName, Math.max(1, segmentSeconds), Math.max(1, maxFrames), Math.max(1, strategy.getMaxSegments()));
    }

    private List<VideoSegmentPlan> buildSegments(String videoId, long durationMs, long segmentSeconds, int maxFrames) {
        long segmentMs = segmentSeconds * 1000L;
        int segmentCount = (int) Math.max(1, Math.ceil((double) durationMs / segmentMs));
        int selectedCount = Math.min(segmentCount, maxFrames);
        List<VideoSegmentPlan> segments = new ArrayList<>();
        for (int index = 0; index < selectedCount; index++) {
            int sourceIndex = selectedCount == segmentCount ? index : (int) Math.floor(index * (double) segmentCount / selectedCount);
            long start = Math.min(durationMs, sourceIndex * segmentMs);
            long end = Math.min(durationMs, Math.max(start + 1000L, start + segmentMs));
            long keyFrameTime = Math.min(Math.max(0L, durationMs - 1000L), start + Math.max(500L, (end - start) / 2));
            segments.add(new VideoSegmentPlan(videoId + "-seg-" + String.format("%04d", sourceIndex + 1), start, end, keyFrameTime));
        }
        return segments;
    }

    private List<VideoSegmentPlan> buildContentAwareSegments(String videoId, long durationMs, List<Long> rawBoundaries, int maxFrames) {
        WorkerPipelineProperties.MediaStrategy strategy = properties.getMediaStrategy();
        long minSegmentMs = strategy.getMinContentSegmentSeconds() * 1000L;
        long maxSegmentMs = strategy.getMaxContentSegmentSeconds() * 1000L;
        List<Long> boundaries = normalizeBoundaries(durationMs, rawBoundaries, minSegmentMs);
        if (boundaries.size() < 2) {
            return List.of();
        }
        List<VideoSegmentPlan> segments = new ArrayList<>();
        for (int i = 0; i < boundaries.size() - 1; i++) {
            splitByMaximum(videoId, segments, boundaries.get(i), boundaries.get(i + 1), maxSegmentMs);
        }
        return limitSegments(segments, maxFrames);
    }

    private List<Long> normalizeBoundaries(long durationMs, List<Long> rawBoundaries, long minSegmentMs) {
        LinkedHashSet<Long> values = new LinkedHashSet<>();
        values.add(0L);
        rawBoundaries.stream()
                .filter(value -> value > 0 && value < durationMs)
                .sorted()
                .forEach(values::add);
        values.add(durationMs);
        List<Long> sorted = values.stream().sorted().toList();
        List<Long> merged = new ArrayList<>();
        for (Long boundary : sorted) {
            if (merged.isEmpty() || boundary - merged.get(merged.size() - 1) >= minSegmentMs || boundary == durationMs) {
                merged.add(boundary);
            }
        }
        if (merged.size() > 1 && durationMs - merged.get(merged.size() - 2) < minSegmentMs) {
            merged.remove(merged.size() - 2);
        }
        return merged;
    }

    private void splitByMaximum(String videoId, List<VideoSegmentPlan> segments, long start, long end, long maxSegmentMs) {
        long cursor = start;
        while (end - cursor > maxSegmentMs) {
            long next = cursor + maxSegmentMs;
            segments.add(segment(videoId, segments.size() + 1, cursor, next));
            cursor = next;
        }
        if (end > cursor) {
            segments.add(segment(videoId, segments.size() + 1, cursor, end));
        }
    }

    private List<VideoSegmentPlan> limitSegments(List<VideoSegmentPlan> segments, int maxFrames) {
        if (segments.size() <= maxFrames) {
            return segments;
        }
        List<VideoSegmentPlan> selected = new ArrayList<>();
        for (int index = 0; index < maxFrames; index++) {
            int sourceIndex = (int) Math.floor(index * (double) segments.size() / maxFrames);
            VideoSegmentPlan source = segments.get(sourceIndex);
            selected.add(new VideoSegmentPlan(
                    source.segmentId().replaceAll("seg-\\d+$", "seg-" + String.format("%04d", index + 1)),
                    source.startTimeMs(),
                    source.endTimeMs(),
                    source.keyFrameTimeMs()));
        }
        return selected;
    }

    private VideoSegmentPlan segment(String videoId, int index, long start, long end) {
        long keyFrameTime = Math.min(Math.max(0L, end - 500L), start + Math.max(500L, (end - start) / 2));
        return new VideoSegmentPlan(videoId + "-seg-" + String.format("%04d", index), start, end, keyFrameTime);
    }

    private long averageDurationMs(List<VideoSegmentPlan> segments) {
        return (long) segments.stream()
                .map(segment -> segment.endTimeMs() - segment.startTimeMs())
                .sorted(Comparator.naturalOrder())
                .mapToLong(Long::longValue)
                .average()
                .orElse(0L);
    }

    private record StrategyChoice(String name, String contentName, long segmentSeconds, int maxFrames, int absoluteMaxSegments) {
        private String name(int selectedSegments) {
            return selectedSegments >= absoluteMaxSegments ? name + "_SAMPLED" : name;
        }

        private String contentName(int selectedSegments) {
            return selectedSegments >= absoluteMaxSegments ? contentName + "_SAMPLED" : contentName;
        }
    }
}
