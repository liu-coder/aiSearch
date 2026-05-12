package com.aisearch.common.asr;

/**
 * 带时间戳的转写文本片段，时间单位为毫秒。
 */
public record TimedTextSegment(
        long startTimeMs,
        long endTimeMs,
        String text,
        Integer speakerId
) {
}
