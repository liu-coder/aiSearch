package com.aisearch.search.application;

import com.aisearch.common.search.RecallSource;
import com.aisearch.search.domain.CandidateSegment;
import com.aisearch.search.domain.QueryIntent;
import java.util.List;

/**
 * 单一路径召回适配器，分别对接 ES、Milvus、元数据或缓存等具体检索能力。
 */
public interface RecallAdapter {
    RecallSource source();

    List<CandidateSegment> recall(QueryIntent intent, int limit);
}
