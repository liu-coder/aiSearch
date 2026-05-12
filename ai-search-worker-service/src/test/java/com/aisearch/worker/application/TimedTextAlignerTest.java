package com.aisearch.worker.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aisearch.common.asr.TimedTextSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ASR 时间戳对齐测试，确保片段索引只使用自身时间范围内的转写文本。
 */
class TimedTextAlignerTest {
    @Test
    void alignsOnlyOverlappedSentencesToSegment() {
        List<TimedTextSegment> segments = List.of(
                new TimedTextSegment(0, 5_000, "开场介绍", null),
                new TimedTextSegment(8_000, 15_000, "产品发布", null),
                new TimedTextSegment(20_000, 30_000, "价格说明", null));

        String text = TimedTextAligner.align(segments, 6_000, 18_000, "fallback");

        assertThat(text).isEqualTo("产品发布");
    }

    @Test
    void includesSentenceCrossingSegmentBoundary() {
        List<TimedTextSegment> segments = List.of(
                new TimedTextSegment(4_000, 9_000, "跨边界句子", null),
                new TimedTextSegment(12_000, 15_000, "片段内句子", null));

        String text = TimedTextAligner.align(segments, 8_000, 13_000, "");

        assertThat(text).isEqualTo("跨边界句子 片段内句子");
    }

    @Test
    void fallsBackWhenTimestampSegmentsAreMissing() {
        String text = TimedTextAligner.align(List.of(), 0, 10_000, "整段兜底文本");

        assertThat(text).isEqualTo("整段兜底文本");
    }
}
