package com.aisearch.search.domain;

import com.aisearch.common.search.RecallSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 召回阶段的内部候选片段，保留分数和召回来源，尚未转换为对外响应。
 */
public record CandidateSegment(
        String videoId,
        String segmentId,
        String title,
        long startTimeMs,
        long endTimeMs,
        double score,
        List<RecallSource> recallSources
) {
    /**
     * 返回一个更新分数后的不可变候选对象。
     */
    public CandidateSegment withScore(double newScore) {
        return new CandidateSegment(videoId, segmentId, title, startTimeMs, endTimeMs, newScore, recallSources);
    }

    /**
     * 合并同一片段的多通道召回结果，保留更宽的时间范围和更多证据来源。
     */
    public CandidateSegment mergeWith(CandidateSegment other) {
        LinkedHashSet<RecallSource> sources = new LinkedHashSet<>(recallSources);
        sources.addAll(other.recallSources);
        double mergedScore = Math.max(score, other.score) + Math.min(score, other.score) * 0.1;
        return new CandidateSegment(
                videoId,
                segmentId,
                title,
                Math.min(startTimeMs, other.startTimeMs),
                Math.max(endTimeMs, other.endTimeMs),
                mergedScore,
                new ArrayList<>(sources));
    }
}
