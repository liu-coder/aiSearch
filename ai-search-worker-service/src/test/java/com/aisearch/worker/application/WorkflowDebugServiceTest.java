package com.aisearch.worker.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aisearch.common.workflow.WorkflowStage;
import com.aisearch.worker.domain.StageTaskStatus;
import com.aisearch.worker.domain.VideoProcessingStageTaskEntity;
import com.aisearch.worker.infrastructure.persistence.VideoProcessingArtifactRepository;
import com.aisearch.worker.infrastructure.persistence.VideoProcessingStageTaskRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowDebugServiceTest {
    @Test
    void rerunStageResetsStageAndLaterStagesToPending() {
        VideoProcessingStageTaskRepository taskRepository = mock(VideoProcessingStageTaskRepository.class);
        VideoProcessingArtifactRepository artifactRepository = mock(VideoProcessingArtifactRepository.class);
        VideoProcessingTaskExecutor executor = mock(VideoProcessingTaskExecutor.class);
        WorkflowDebugService service = new WorkflowDebugService(taskRepository, artifactRepository, executor, null);
        var asr = task(WorkflowStage.ASR_PROCESSING, StageTaskStatus.FAILED, 4);
        var ocr = task(WorkflowStage.OCR_PROCESSING, StageTaskStatus.SUCCEEDED, 5);
        when(taskRepository.findByVideoIdOrderByStageSequenceAsc("video-1")).thenReturn(List.of(asr, ocr));

        service.rerunStage("video-1", WorkflowStage.ASR_PROCESSING);

        assertThat(asr.getStatus()).isEqualTo(StageTaskStatus.PENDING);
        assertThat(ocr.getStatus()).isEqualTo(StageTaskStatus.PENDING);
        verify(artifactRepository).deleteByVideoIdAndArtifactTypeIn("video-1",
                List.of("ASR_SEGMENTS", "ASR_TEXT", "OCR_TEXT", "CAPTION", "EMBEDDING_TEXT", "READY"));
    }

    @Test
    void rebuildIndexOnlyRerunsEmbeddingAndIndexingTail() {
        VideoProcessingStageTaskRepository taskRepository = mock(VideoProcessingStageTaskRepository.class);
        VideoProcessingArtifactRepository artifactRepository = mock(VideoProcessingArtifactRepository.class);
        VideoProcessingTaskExecutor executor = mock(VideoProcessingTaskExecutor.class);
        WorkflowDebugService service = new WorkflowDebugService(taskRepository, artifactRepository, executor, null);
        var embedding = task(WorkflowStage.EMBEDDING, StageTaskStatus.SUCCEEDED, 7);
        var indexing = task(WorkflowStage.INDEXING, StageTaskStatus.SUCCEEDED, 8);
        when(taskRepository.findByVideoIdOrderByStageSequenceAsc("video-1")).thenReturn(List.of(embedding, indexing));

        service.rebuildIndex("video-1");

        assertThat(embedding.getStatus()).isEqualTo(StageTaskStatus.PENDING);
        assertThat(indexing.getStatus()).isEqualTo(StageTaskStatus.PENDING);
        verify(artifactRepository).deleteByVideoIdAndArtifactTypeIn("video-1", List.of("EMBEDDING_TEXT", "READY"));
    }

    private VideoProcessingStageTaskEntity task(WorkflowStage stage, StageTaskStatus status, int sequence) {
        return VideoProcessingStageTaskEntity.planned("event-1", "video-1", "bucket", "object", stage, status, sequence);
    }
}
