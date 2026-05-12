package com.aisearch.worker.application;

/**
 * 文本检索索引写入端口。
 */
public interface SearchIndexWriter {
    void index(IndexSegmentDocument document);

    void deleteByVideoId(String videoId);
}
