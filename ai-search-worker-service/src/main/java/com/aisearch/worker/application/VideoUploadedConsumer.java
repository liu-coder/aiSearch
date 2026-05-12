package com.aisearch.worker.application;

import com.aisearch.common.video.VideoUploadedMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * video.uploaded 消费者，负责把上传完成消息转换为离线索引工作流启动命令。
 */
@Component
@RocketMQMessageListener(
        topic = "${ai-search.rocketmq.video-uploaded-topic}",
        consumerGroup = "${ai-search.rocketmq.video-uploaded-consumer-group}")
public class VideoUploadedConsumer implements RocketMQListener<VideoUploadedMessage> {
    private final VideoIndexingWorkflowService workflowService;

    public VideoUploadedConsumer(VideoIndexingWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public void onMessage(VideoUploadedMessage message) {
        workflowService.start(message);
    }
}
