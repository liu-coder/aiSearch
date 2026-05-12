package com.aisearch.worker.application;

import com.aisearch.worker.domain.StageFailureType;

/**
 * 阶段处理异常，携带失败分类和是否可重试。
 */
public class StageProcessingException extends RuntimeException {
    private final StageFailureType failureType;
    private final boolean retryable;

    public StageProcessingException(StageFailureType failureType, boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
        this.retryable = retryable;
    }

    public StageFailureType failureType() {
        return failureType;
    }

    public boolean retryable() {
        return retryable;
    }
}
