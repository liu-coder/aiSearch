package com.aisearch.search.application;

import com.aisearch.common.search.Evidence;
import com.aisearch.common.search.SearchResultItem;
import com.aisearch.search.domain.CandidateSegment;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 证据构建服务，将召回来源和片段信息转换为前端与 LLM 都能消费的证据结构。
 */
@Service
public class EvidenceService {
    public List<SearchResultItem> build(List<CandidateSegment> candidates) {
        return candidates.stream().map(this::toResult).toList();
    }

    private SearchResultItem toResult(CandidateSegment candidate) {
        List<Evidence> evidence = candidate.recallSources().stream()
                .map(source -> new Evidence(source.name(), "命中通道：" + source.name(), candidate.score()))
                .toList();
        return new SearchResultItem(
                candidate.videoId(),
                candidate.segmentId(),
                candidate.title(),
                candidate.startTimeMs(),
                candidate.endTimeMs(),
                candidate.score(),
                candidate.recallSources(),
                evidence);
    }
}
