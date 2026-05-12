package com.aisearch.worker.application;

import com.aisearch.common.workflow.WorkflowStage;
import com.aisearch.worker.domain.StageTaskStatus;
import java.util.List;

/**
 * 一次视频入库消费生成的阶段任务计划，后续由具体处理器按阶段领取执行。
 */
public record VideoIndexingTaskPlan(
        String eventId,
        String videoId,
        List<StageTask> stageTasks
) {
    public record StageTask(
            WorkflowStage stage,
            StageTaskStatus status,
            int sequence
    ) {
    }
}
