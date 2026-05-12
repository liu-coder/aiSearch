package com.aisearch.video.infrastructure.event;

/**
 * 视频处理事件发布端口，避免应用层绑定具体 MQ SDK。
 */
public interface VideoProcessingEventPublisher {
    void publishUploaded(VideoUploadedEvent event);
}
