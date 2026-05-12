package com.aisearch.search.application;

import com.aisearch.search.domain.CandidateSegment;
import com.aisearch.search.domain.QueryIntent;
import java.util.List;

/**
 * 多路召回编排接口。生产实现会聚合 Elasticsearch、Milvus、OCR、ASR 和元数据过滤结果。
 */
public interface RecallOrchestrator {
    List<CandidateSegment> recall(QueryIntent intent, int limit);
}
