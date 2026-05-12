package com.aisearch.worker.infrastructure.persistence;

import com.aisearch.common.video.VideoUploadedMessage;
import com.aisearch.common.workflow.WorkflowStage;
import com.aisearch.worker.application.VideoIndexingTaskPlan;
import com.aisearch.worker.application.VideoProcessingTaskStore;
import com.aisearch.worker.domain.StageTaskStatus;
import com.aisearch.worker.domain.VideoProcessingStageTaskEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 MySQL/JPA 的任务计划存储实现，服务重启后仍可继续调度未完成阶段。
 */
@Repository
public class JpaVideoProcessingTaskStore implements VideoProcessingTaskStore {
    private final VideoProcessingStageTaskRepository repository;

    public JpaVideoProcessingTaskStore(VideoProcessingStageTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return repository.existsByEventId(eventId);
    }

    @Override
    @Transactional
    public VideoIndexingTaskPlan createPlan(VideoUploadedMessage message, List<WorkflowStage> stages) {
        List<VideoProcessingStageTaskEntity> entities = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            WorkflowStage stage = stages.get(i);
            StageTaskStatus status = initialStatus(stage);
            entities.add(VideoProcessingStageTaskEntity.planned(
                    message.eventId(),
                    message.videoId(),
                    message.bucket(),
                    message.objectKey(),
                    stage,
                    status,
                    i + 1));
        }
        return toPlan(message.eventId(), message.videoId(), repository.saveAll(entities));
    }

    private StageTaskStatus initialStatus(WorkflowStage stage) {
        return stage == WorkflowStage.UPLOADED ? StageTaskStatus.SUCCEEDED : StageTaskStatus.PENDING;
    }

    private VideoIndexingTaskPlan toPlan(String eventId, String videoId, List<VideoProcessingStageTaskEntity> entities) {
        List<VideoIndexingTaskPlan.StageTask> stageTasks = entities.stream()
                .map(entity -> new VideoIndexingTaskPlan.StageTask(
                        entity.getStage(),
                        entity.getStatus(),
                        entity.getStageSequence()))
                .toList();
        return new VideoIndexingTaskPlan(eventId, videoId, stageTasks);
    }
}
