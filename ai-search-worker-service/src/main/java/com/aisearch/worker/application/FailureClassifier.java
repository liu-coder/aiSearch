package com.aisearch.worker.application;

import com.aisearch.worker.domain.StageFailureType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 失败分类器，把底层异常归一成可观测、可重试的阶段失败类型。
 */
@Component
public class FailureClassifier {
    public StageProcessingException classify(RuntimeException ex) {
        if (ex instanceof StageProcessingException classified) {
            return classified;
        }
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        if (ex instanceof ResourceAccessException) {
            return new StageProcessingException(StageFailureType.MODEL_TIMEOUT, true, message, ex);
        }
        if (ex instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            if (status == 429) {
                return new StageProcessingException(StageFailureType.MODEL_RATE_LIMIT, true, message, ex);
            }
            if (status >= 500) {
                return new StageProcessingException(StageFailureType.MODEL_RESPONSE, true, message, ex);
            }
            return new StageProcessingException(StageFailureType.MODEL_RESPONSE, false, message, ex);
        }
        if (message.contains("MinIO") || message.contains("对象")) {
            return new StageProcessingException(StageFailureType.STORAGE, true, message, ex);
        }
        if (message.contains("FFmpeg") || message.contains("ffprobe") || message.contains("媒体命令")) {
            return new StageProcessingException(StageFailureType.MEDIA, false, message, ex);
        }
        if (message.contains("Elasticsearch") || message.contains("Milvus") || message.contains("索引")) {
            return new StageProcessingException(StageFailureType.SEARCH_INDEX, true, message, ex);
        }
        if (message.contains("未配置") || message.contains("配置")) {
            return new StageProcessingException(StageFailureType.CONFIGURATION, false, message, ex);
        }
        return new StageProcessingException(StageFailureType.UNKNOWN, true, message, ex);
    }
}
