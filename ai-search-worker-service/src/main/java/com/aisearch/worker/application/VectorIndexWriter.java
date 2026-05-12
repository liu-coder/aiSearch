package com.aisearch.worker.application;

/**
 * 向量索引写入端口。
 */
public interface VectorIndexWriter {
    void upsert(IndexSegmentDocument document);

    void deleteByVideoId(String videoId);
}
