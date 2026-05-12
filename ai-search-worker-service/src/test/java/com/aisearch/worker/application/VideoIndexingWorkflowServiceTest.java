package com.aisearch.worker.application;

import com.aisearch.common.video.VideoUploadedMessage;
import com.aisearch.common.workflow.WorkflowStage;
import com.aisearch.worker.domain.StageTaskStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证离线索引工作流会生成可持久化的阶段任务计划，并对重复消息保持幂等。
 */
class VideoIndexingWorkflowServiceTest {
    @Test
    void createsStagePlanForUploadedVideo() {
        InMemoryTaskStore taskStore = new InMemoryTaskStore(false);
        VideoIndexingWorkflowService workflowService =
                new VideoIndexingWorkflowService(new WorkflowPlanService(), taskStore);

        workflowService.start(uploadedMessage());

        assertThat(taskStore.createdPlans).hasSize(1);
        assertThat(taskStore.createdPlans.get(0).stageTasks())
                .extracting(VideoIndexingTaskPlan.StageTask::stage)
                .contains(WorkflowStage.UPLOADED, WorkflowStage.TRANSCODING, WorkflowStage.INDEXING);
    }

    @Test
    void ignoresDuplicateUploadedEvent() {
        InMemoryTaskStore taskStore = new InMemoryTaskStore(true);
        VideoIndexingWorkflowService workflowService =
                new VideoIndexingWorkflowService(new WorkflowPlanService(), taskStore);

        workflowService.start(uploadedMessage());

        assertThat(taskStore.createdPlans).isEmpty();
    }

    private VideoUploadedMessage uploadedMessage() {
        return new VideoUploadedMessage(
                "event-1",
                "video-1",
                "ai-video-raw",
                "raw/video-1/demo.mp4",
                1024,
                "video/mp4",
                Instant.now());
    }

    private static class InMemoryTaskStore implements VideoProcessingTaskStore {
        private final boolean eventExists;
        private final List<VideoIndexingTaskPlan> createdPlans = new ArrayList<>();

        private InMemoryTaskStore(boolean eventExists) {
            this.eventExists = eventExists;
        }

        @Override
        public boolean existsByEventId(String eventId) {
            return eventExists;
        }

        @Override
        public VideoIndexingTaskPlan createPlan(VideoUploadedMessage message, List<WorkflowStage> stages) {
            List<VideoIndexingTaskPlan.StageTask> stageTasks = new ArrayList<>();
            for (int i = 0; i < stages.size(); i++) {
                stageTasks.add(new VideoIndexingTaskPlan.StageTask(
                        stages.get(i),
                        i == 0 ? StageTaskStatus.SUCCEEDED : StageTaskStatus.PENDING,
                        i + 1));
            }
            VideoIndexingTaskPlan plan = new VideoIndexingTaskPlan(message.eventId(), message.videoId(), stageTasks);
            createdPlans.add(plan);
            return plan;
        }
    }
}
