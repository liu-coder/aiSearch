package com.aisearch.common.workflow;

/**
 * 视频离线入库状态机阶段，用于 worker 任务编排、重试和可观测。
 */
public enum WorkflowStage {
    UPLOADED,
    TRANSCODING,
    FRAME_EXTRACTING,
    ASR_PROCESSING,
    OCR_PROCESSING,
    CAPTIONING,
    EMBEDDING,
    INDEXING,
    READY,
    FAILED
}
