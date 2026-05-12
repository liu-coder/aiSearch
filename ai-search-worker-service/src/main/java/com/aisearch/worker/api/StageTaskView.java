package com.aisearch.worker.api;

import com.aisearch.common.workflow.WorkflowStage;
import com.aisearch.worker.domain.StageFailureType;
import com.aisearch.worker.domain.StageTaskStatus;

/**
 * 阶段任务调试视图，用于端到端联调查看每个阶段状态。
 */
public record StageTaskView(
        WorkflowStage stage,
        StageTaskStatus status,
        int sequence,
        int attempts,
        StageFailureType failureType,
        String failureReason
) {
}
