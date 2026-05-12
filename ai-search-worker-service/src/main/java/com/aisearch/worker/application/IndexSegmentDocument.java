package com.aisearch.worker.application;

import java.util.List;

/**
 * 写入 ES/Milvus 的视频片段索引文档。
 */
public record IndexSegmentDocument(
        String videoId,
        String segmentId,
        String title,
        long startTimeMs,
        long endTimeMs,
        String indexVersion,
        String asrText,
        String ocrText,
        String caption,
        List<Double> embedding,
        List<Double> imageEmbedding
) {
}
