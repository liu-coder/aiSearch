package com.aisearch.worker.application;

import com.aisearch.common.asr.TimedTextSegment;
import java.util.List;

/**
 * 将带时间戳的 ASR 句子对齐到视频切片时间范围。
 */
final class TimedTextAligner {
    private TimedTextAligner() {
    }

    /**
     * 提取与目标切片时间范围重叠的 ASR 文本，避免整段字幕污染每个片段的 embedding。
     */
    static String align(List<TimedTextSegment> asrSegments, long segmentStartMs, long segmentEndMs, String fallbackText) {
        if (asrSegments == null || asrSegments.isEmpty()) {
            return fallbackText == null ? "" : fallbackText;
        }
        return asrSegments.stream()
                .filter(segment -> overlaps(segment, segmentStartMs, segmentEndMs))
                .map(TimedTextSegment::text)
                .filter(value -> value != null && !value.isBlank())
                .reduce("", (left, right) -> left.isBlank() ? right : left + " " + right);
    }

    private static boolean overlaps(TimedTextSegment segment, long segmentStartMs, long segmentEndMs) {
        long start = segment.startTimeMs();
        long end = segment.endTimeMs() <= 0 ? start + 1 : segment.endTimeMs();
        return start < segmentEndMs && end > segmentStartMs;
    }
}
