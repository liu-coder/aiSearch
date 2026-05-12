package com.aisearch.worker.application;

import org.springframework.stereotype.Service;

/**
 * 搜索索引维护服务，负责重建前清理 ES/Milvus 中的旧片段，避免残留召回。
 */
@Service
public class SearchIndexMaintenanceService {
    private final SearchIndexWriter searchIndexWriter;
    private final VectorIndexWriter vectorIndexWriter;

    public SearchIndexMaintenanceService(SearchIndexWriter searchIndexWriter, VectorIndexWriter vectorIndexWriter) {
        this.searchIndexWriter = searchIndexWriter;
        this.vectorIndexWriter = vectorIndexWriter;
    }

    public void deleteVideo(String videoId) {
        searchIndexWriter.deleteByVideoId(videoId);
        vectorIndexWriter.deleteByVideoId(videoId);
    }
}
