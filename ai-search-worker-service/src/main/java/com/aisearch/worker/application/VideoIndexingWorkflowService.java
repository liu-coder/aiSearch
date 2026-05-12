package com.aisearch.worker.application;

import com.aisearch.common.video.VideoUploadedMessage;
import com.aisearch.common.workflow.WorkflowStage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 视频离线索引工作流入口。当前先记录任务阶段，后续逐步接入 FFmpeg、ASR、OCR、Caption 和索引构建。
 */
@Service
public class VideoIndexingWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(VideoIndexingWorkflowService.class);

    private final WorkflowPlanService workflowPlanService;
    private final VideoProcessingTaskStore taskStore;

    public VideoIndexingWorkflowService(WorkflowPlanService workflowPlanService, VideoProcessingTaskStore taskStore) {
        this.workflowPlanService = workflowPlanService;
        this.taskStore = taskStore;
    }

    public void start(VideoUploadedMessage message) {
        if (taskStore.existsByEventId(message.eventId())) {
            log.info("video_indexing_duplicate_ignored eventId={} videoId={}", message.eventId(), message.videoId());
            return;
        }
        List<WorkflowStage> stages = workflowPlanService.videoIndexingStages();
        VideoIndexingTaskPlan plan = taskStore.createPlan(message, stages);
        log.info("video_indexing_started eventId={} videoId={} bucket={} objectKey={} stages={}",
                message.eventId(), message.videoId(), message.bucket(), message.objectKey(), plan.stageTasks());
    }
}
