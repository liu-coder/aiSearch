package com.aisearch.video.infrastructure.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 本地降级事件发布实现，仅在 local profile 下启用。
 */
@Component
@Profile("local")
public class LoggingVideoProcessingEventPublisher implements VideoProcessingEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingVideoProcessingEventPublisher.class);

    @Override
    public void publishUploaded(VideoUploadedEvent event) {
        log.info("video_uploaded_event videoId={} bucket={} objectKey={} fileSize={} contentType={}",
                event.videoId(), event.bucket(), event.objectKey(), event.fileSize(), event.contentType());
    }
}
