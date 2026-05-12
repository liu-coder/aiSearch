package com.aisearch.worker.infrastructure.index;

import com.aisearch.worker.application.IndexSegmentDocument;
import com.aisearch.worker.application.VectorIndexWriter;
import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Milvus REST 写入器，把片段向量和关键元数据写入向量 collection。
 */
@Component
public class MilvusVectorIndexWriter implements VectorIndexWriter {
    private final RestClient restClient;
    private final WorkerPipelineProperties properties;

    public MilvusVectorIndexWriter(RestClient.Builder builder, WorkerPipelineProperties properties) {
        this.restClient = builder.baseUrl(properties.getMilvus().getEndpoint()).build();
        this.properties = properties;
    }

    @Override
    public void upsert(IndexSegmentDocument document) {
        restClient.post()
                .uri("/v2/vectordb/entities/insert")
                .body(Map.of(
                        "collectionName", properties.getMilvus().getCollectionName(),
                        "data", List.of(Map.of(
                                "videoId", document.videoId(),
                                "segmentId", document.segmentId(),
                                "title", document.title(),
                                "startTimeMs", document.startTimeMs(),
                                "endTimeMs", document.endTimeMs(),
                                properties.getMilvus().getVectorField(), document.embedding(),
                                properties.getMilvus().getImageVectorField(), document.imageEmbedding()))))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void deleteByVideoId(String videoId) {
        restClient.post()
                .uri("/v2/vectordb/entities/delete")
                .body(Map.of(
                        "collectionName", properties.getMilvus().getCollectionName(),
                        "filter", "videoId == \"" + videoId.replace("\"", "\\\"") + "\""))
                .retrieve()
                .toBodilessEntity();
    }
}
