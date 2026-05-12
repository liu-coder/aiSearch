package com.aisearch.search.infrastructure;

import com.aisearch.common.search.RecallSource;
import com.aisearch.search.application.RecallAdapter;
import com.aisearch.search.domain.CandidateSegment;
import com.aisearch.search.domain.QueryIntent;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 本地开发召回适配器，只在 local/test profile 下提供固定候选，避免生产返回演示数据。
 */
@Component
@Profile({"local", "test"})
public class LocalFixtureRecallAdapter implements RecallAdapter {
    private final RecallSource source;

    public LocalFixtureRecallAdapter() {
        this(RecallSource.KEYWORD);
    }

    LocalFixtureRecallAdapter(RecallSource source) {
        this.source = source;
    }

    @Override
    public RecallSource source() {
        return source;
    }

    @Override
    public List<CandidateSegment> recall(QueryIntent intent, int limit) {
        CandidateSegment candidate = switch (source) {
            case IMAGE_VECTOR, SEGMENT_VECTOR -> new CandidateSegment(
                    "video-002",
                    "seg-004",
                    "城市道路汽车航拍素材",
                    12_000,
                    27_000,
                    0.78,
                    List.of(RecallSource.IMAGE_VECTOR, RecallSource.SEGMENT_VECTOR));
            case OCR, METADATA -> new CandidateSegment(
                    "video-003",
                    "seg-002",
                    "AI 搜索产品演示",
                    31_000,
                    56_000,
                    0.64,
                    List.of(RecallSource.OCR, RecallSource.METADATA));
            default -> new CandidateSegment(
                    "video-001",
                    "seg-001",
                    "新能源车发布会实录",
                    83_000,
                    103_000,
                    0.82,
                    List.of(RecallSource.KEYWORD, RecallSource.TEXT_VECTOR, RecallSource.ASR));
        };
        return List.of(candidate).stream().limit(limit).toList();
    }
}
