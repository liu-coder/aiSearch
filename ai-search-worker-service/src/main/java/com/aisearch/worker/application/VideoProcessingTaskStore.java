package com.aisearch.worker.application;

import com.aisearch.common.video.VideoUploadedMessage;
import com.aisearch.common.workflow.WorkflowStage;
import java.util.List;

/**
 * 离线任务持久化端口，把业务编排和具体数据库实现隔离开。
 */
public interface VideoProcessingTaskStore {
    boolean existsByEventId(String eventId);

    VideoIndexingTaskPlan createPlan(VideoUploadedMessage message, List<WorkflowStage> stages);
}
