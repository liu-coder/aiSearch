package com.aisearch.worker.domain;

/**
 * 离线阶段任务状态，用于支持幂等消费、重试调度和失败定位。
 */
public enum StageTaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
}
