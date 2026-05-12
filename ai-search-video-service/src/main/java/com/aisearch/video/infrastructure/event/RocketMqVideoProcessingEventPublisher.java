package com.aisearch.video.infrastructure.event;

import com.aisearch.common.video.VideoUploadedMessage;
import java.time.Instant;
import java.util.UUID;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 事件发布实现。上传完成后把视频处理任务投递给 worker 服务。
 */
@Component
@Profile("!local")
public class RocketMqVideoProcessingEventPublisher implements VideoProcessingEventPublisher {
    private final RocketMQTemplate rocketMQTemplate;
    private final String videoUploadedTopic;

    public RocketMqVideoProcessingEventPublisher(
            RocketMQTemplate rocketMQTemplate,
            @Value("${ai-search.rocketmq.video-uploaded-topic}") String videoUploadedTopic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.videoUploadedTopic = videoUploadedTopic;
    }

    @Override
    public void publishUploaded(VideoUploadedEvent event) {
        VideoUploadedMessage message = new VideoUploadedMessage(
                UUID.randomUUID().toString(),
                event.videoId(),
                event.bucket(),
                event.objectKey(),
                event.fileSize(),
                event.contentType(),
                Instant.now());
        rocketMQTemplate.convertAndSend(videoUploadedTopic, message);
    }
}
