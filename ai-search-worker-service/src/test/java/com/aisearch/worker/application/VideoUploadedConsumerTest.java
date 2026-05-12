package com.aisearch.worker.application;

import com.aisearch.common.video.VideoUploadedMessage;
import com.aisearch.common.workflow.WorkflowStage;
import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 RocketMQ 消费者收到上传完成消息后会启动离线索引工作流。
 */
class VideoUploadedConsumerTest {
    @Test
    void startsWorkflowWhenMessageArrives() {
        SpyWorkflowService workflowService = new SpyWorkflowService();
        VideoUploadedConsumer consumer = new VideoUploadedConsumer(workflowService);
        VideoUploadedMessage message = new VideoUploadedMessage(
                "event-1",
                "video-1",
                "ai-video-raw",
                "raw/video-1/demo.mp4",
                1024,
                "video/mp4",
                Instant.now());

        consumer.onMessage(message);

        assertThat(workflowService.lastMessage).isEqualTo(message);
    }

    private static class SpyWorkflowService extends VideoIndexingWorkflowService {
        private VideoUploadedMessage lastMessage;

        private SpyWorkflowService() {
            super(new WorkflowPlanService(), new InMemoryTaskStore());
        }

        @Override
        public void start(VideoUploadedMessage message) {
            this.lastMessage = message;
        }
    }

    private static class InMemoryTaskStore implements VideoProcessingTaskStore {
        @Override
        public boolean existsByEventId(String eventId) {
            return false;
        }

        @Override
        public VideoIndexingTaskPlan createPlan(VideoUploadedMessage message, List<WorkflowStage> stages) {
            return new VideoIndexingTaskPlan(message.eventId(), message.videoId(), List.of());
        }
    }
}
