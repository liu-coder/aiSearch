package com.aisearch.worker.infrastructure.index;

import com.aisearch.worker.application.IndexSegmentDocument;
import com.aisearch.worker.application.SearchIndexWriter;
import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Elasticsearch 索引写入器，把离线产物写成搜索服务可召回的视频片段文档。
 */
@Component
public class ElasticsearchIndexWriter implements SearchIndexWriter {
    private final RestClient restClient;
    private final WorkerPipelineProperties properties;

    public ElasticsearchIndexWriter(RestClient.Builder builder, WorkerPipelineProperties properties) {
        this.restClient = builder.baseUrl(properties.getElasticsearch().getEndpoint()).build();
        this.properties = properties;
    }

    @Override
    public void index(IndexSegmentDocument document) {
        restClient.put()
                .uri("/{index}/_doc/{id}", properties.getElasticsearch().getIndex(), document.segmentId())
                .body(Map.of(
                        "videoId", document.videoId(),
                        "segmentId", document.segmentId(),
                        "title", document.title(),
                        "startTimeMs", document.startTimeMs(),
                        "endTimeMs", document.endTimeMs(),
                        "indexVersion", document.indexVersion(),
                        "asrText", document.asrText(),
                        "ocrText", document.ocrText(),
                        "caption", document.caption()))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void deleteByVideoId(String videoId) {
        restClient.post()
                .uri("/{index}/_delete_by_query", properties.getElasticsearch().getIndex())
                .body(Map.of("query", Map.of("term", Map.of("videoId", videoId))))
                .retrieve()
                .toBodilessEntity();
    }
}
