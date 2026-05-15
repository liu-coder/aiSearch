package com.aisearch.worker.application;

import com.aisearch.common.workflow.WorkflowStage;
import com.aisearch.worker.domain.StageFailureType;
import com.aisearch.worker.domain.StageTaskStatus;
import com.aisearch.worker.domain.VideoProcessingStageTaskEntity;
import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import com.aisearch.worker.infrastructure.persistence.VideoProcessingStageTaskRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoProcessingTaskExecutorMetricsTest {
    @Test
    void recordsFailureMetricForTerminalStageFailure() {
        VideoProcessingStageTaskRepository repository = mock(VideoProcessingStageTaskRepository.class);
        VideoAssetStatusService statusService = mock(VideoAssetStatusService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        VideoProcessingStageTaskEntity task = VideoProcessingStageTaskEntity.planned(
                "event-1", "video-1", "bucket", "object", WorkflowStage.OCR_PROCESSING, StageTaskStatus.PENDING, 5);
        ReflectionTestUtils.setField(task, "id", 10L);
        StageProcessor failingProcessor = new StageProcessor() {
            @Override
            public WorkflowStage stage() {
                return WorkflowStage.OCR_PROCESSING;
            }

            @Override
            public void process(VideoProcessingStageTaskEntity task) {
                throw new StageProcessingException(StageFailureType.MODEL_RESPONSE, false, "bad model payload", null);
            }
        };
        when(repository.claimPendingTask(eq(10L), any(Instant.class))).thenReturn(1);
        when(repository.findById(10L)).thenReturn(Optional.of(task));

        VideoProcessingTaskExecutor executor = new VideoProcessingTaskExecutor(
                repository,
                new WorkerPipelineProperties(),
                new FailureClassifier(),
                statusService,
                List.of(failingProcessor),
                meterRegistry);

        executor.executeOne(10L);

        assertThat(task.getStatus()).isEqualTo(StageTaskStatus.FAILED);
        assertThat(meterRegistry.counter(
                "ai_search_worker_stage_task_total",
                "stage", "OCR_PROCESSING",
                "result", "failed",
                "failureType", "MODEL_RESPONSE").count()).isEqualTo(1.0);
    }
}
