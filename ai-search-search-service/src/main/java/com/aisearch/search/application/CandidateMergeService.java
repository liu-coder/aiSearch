package com.aisearch.search.application;

import com.aisearch.search.domain.CandidateSegment;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 候选融合服务，负责对多路召回结果去重并保留最强证据。
 */
@Service
public class CandidateMergeService {
    public List<CandidateSegment> merge(List<CandidateSegment> candidates) {
        Map<String, CandidateSegment> bestBySegment = new LinkedHashMap<>();
        for (CandidateSegment candidate : candidates) {
            // 以视频 + 片段为去重粒度，避免同一片段在多个召回通道重复展示。
            String key = candidate.videoId() + ":" + candidate.segmentId();
            bestBySegment.merge(key, candidate, CandidateSegment::mergeWith);
        }
        return bestBySegment.values().stream()
                .sorted(Comparator.comparingDouble(CandidateSegment::score).reversed())
                .toList();
    }
}
