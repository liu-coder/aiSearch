package com.aisearch.worker.application;

import com.aisearch.common.workflow.WorkflowStage;
import com.aisearch.worker.infrastructure.model.ModelGatewayClient;
import com.aisearch.worker.infrastructure.persistence.VideoAssetReadRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为每个阶段注册处理器实例，保持调度器按 WorkflowStage 精确查找。
 */
@Configuration
public class StageProcessorConfig {
    @Bean
    StageProcessor transcodingProcessor(
            ArtifactService artifactService,
            VideoAssetReadRepository assetRepository,
            MediaProcessingService mediaProcessingService,
            ModelGatewayClient modelGatewayClient,
            SegmentArtifactService segmentArtifactService,
            SearchIndexWriter searchIndexWriter,
            VectorIndexWriter vectorIndexWriter) {
        return processor(WorkflowStage.TRANSCODING, artifactService, assetRepository, mediaProcessingService, modelGatewayClient, segmentArtifactService, searchIndexWriter, vectorIndexWriter);
    }

    @Bean
    StageProcessor frameExtractingProcessor(
            ArtifactService artifactService,
            VideoAssetReadRepository assetRepository,
            MediaProcessingService mediaProcessingService,
            ModelGatewayClient modelGatewayClient,
            SegmentArtifactService segmentArtifactService,
            SearchIndexWriter searchIndexWriter,
            VectorIndexWriter vectorIndexWriter) {
        return processor(WorkflowStage.FRAME_EXTRACTING, artifactService, assetRepository, mediaProcessingService, modelGatewayClient, segmentArtifactService, searchIndexWriter, vectorIndexWriter);
    }

    @Bean
    StageProcessor asrProcessor(
            ArtifactService artifactService,
            VideoAssetReadRepository assetRepository,
            MediaProcessingService mediaProcessingService,
            ModelGatewayClient modelGatewayClient,
            SegmentArtifactService segmentArtifactService,
            SearchIndexWriter searchIndexWriter,
            VectorIndexWriter vectorIndexWriter) {
        return processor(WorkflowStage.ASR_PROCESSING, artifactService, assetRepository, mediaProcessingService, modelGatewayClient, segmentArtifactService, searchIndexWriter, vectorIndexWriter);
    }

    @Bean
    StageProcessor ocrProcessor(
            ArtifactService artifactService,
            VideoAssetReadRepository assetRepository,
            MediaProcessingService mediaProcessingService,
            ModelGatewayClient modelGatewayClient,
            SegmentArtifactService segmentArtifactService,
            SearchIndexWriter searchIndexWriter,
            VectorIndexWriter vectorIndexWriter) {
        return processor(WorkflowStage.OCR_PROCESSING, artifactService, assetRepository, mediaProcessingService, modelGatewayClient, segmentArtifactService, searchIndexWriter, vectorIndexWriter);
    }

    @Bean
    StageProcessor captionProcessor(
            ArtifactService artifactService,
            VideoAssetReadRepository assetRepository,
            MediaProcessingService mediaProcessingService,
            ModelGatewayClient modelGatewayClient,
            SegmentArtifactService segmentArtifactService,
            SearchIndexWriter searchIndexWriter,
            VectorIndexWriter vectorIndexWriter) {
        return processor(WorkflowStage.CAPTIONING, artifactService, assetRepository, mediaProcessingService, modelGatewayClient, segmentArtifactService, searchIndexWriter, vectorIndexWriter);
    }

    @Bean
    StageProcessor embeddingProcessor(
            ArtifactService artifactService,
            VideoAssetReadRepository assetRepository,
            MediaProcessingService mediaProcessingService,
            ModelGatewayClient modelGatewayClient,
            SegmentArtifactService segmentArtifactService,
            SearchIndexWriter searchIndexWriter,
            VectorIndexWriter vectorIndexWriter) {
        return processor(WorkflowStage.EMBEDDING, artifactService, assetRepository, mediaProcessingService, modelGatewayClient, segmentArtifactService, searchIndexWriter, vectorIndexWriter);
    }

    @Bean
    StageProcessor indexingProcessor(
            ArtifactService artifactService,
            VideoAssetReadRepository assetRepository,
            MediaProcessingService mediaProcessingService,
            ModelGatewayClient modelGatewayClient,
            SegmentArtifactService segmentArtifactService,
            SearchIndexWriter searchIndexWriter,
            VectorIndexWriter vectorIndexWriter) {
        return processor(WorkflowStage.INDEXING, artifactService, assetRepository, mediaProcessingService, modelGatewayClient, segmentArtifactService, searchIndexWriter, vectorIndexWriter);
    }

    @Bean
    StageProcessor readyProcessor(
            ArtifactService artifactService,
            VideoAssetReadRepository assetRepository,
            MediaProcessingService mediaProcessingService,
            ModelGatewayClient modelGatewayClient,
            SegmentArtifactService segmentArtifactService,
            SearchIndexWriter searchIndexWriter,
            VectorIndexWriter vectorIndexWriter) {
        return processor(WorkflowStage.READY, artifactService, assetRepository, mediaProcessingService, modelGatewayClient, segmentArtifactService, searchIndexWriter, vectorIndexWriter);
    }

    private StageProcessor processor(
            WorkflowStage stage,
            ArtifactService artifactService,
            VideoAssetReadRepository assetRepository,
            MediaProcessingService mediaProcessingService,
            ModelGatewayClient modelGatewayClient,
            SegmentArtifactService segmentArtifactService,
            SearchIndexWriter searchIndexWriter,
            VectorIndexWriter vectorIndexWriter) {
        return new DefaultStageProcessorAdapter(stage, artifactService, assetRepository, mediaProcessingService, modelGatewayClient, segmentArtifactService, searchIndexWriter, vectorIndexWriter);
    }

    private static class DefaultStageProcessorAdapter extends DefaultStageProcessor {
        private final WorkflowStage stage;

        DefaultStageProcessorAdapter(
                WorkflowStage stage,
                ArtifactService artifactService,
                VideoAssetReadRepository assetRepository,
                MediaProcessingService mediaProcessingService,
                ModelGatewayClient modelGatewayClient,
                SegmentArtifactService segmentArtifactService,
                SearchIndexWriter searchIndexWriter,
                VectorIndexWriter vectorIndexWriter) {
            super(artifactService, assetRepository, mediaProcessingService, modelGatewayClient, segmentArtifactService, searchIndexWriter, vectorIndexWriter);
            this.stage = stage;
        }

        @Override
        public WorkflowStage stage() {
            return stage;
        }
    }
}
