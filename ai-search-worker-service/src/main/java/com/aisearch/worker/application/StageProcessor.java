package com.aisearch.worker.application;

import com.aisearch.common.workflow.WorkflowStage;
import com.aisearch.worker.domain.VideoProcessingStageTaskEntity;

/**
 * 单个离线阶段处理器，后续 ASR/OCR/Caption 可替换为真实模型供应商实现。
 */
public interface StageProcessor {
    WorkflowStage stage();

    void process(VideoProcessingStageTaskEntity task);
}
