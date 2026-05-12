package com.aisearch.video.application;

import com.aisearch.video.domain.EventOutboxEntity;
import com.aisearch.video.infrastructure.event.VideoUploadedEvent;
import com.aisearch.video.infrastructure.persistence.EventOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * 视频上传 outbox 写入服务，保证和视频资产状态更新处在同一个本地事务中。
 */
@Service
public class VideoUploadOutboxService {
    public static final String VIDEO_UPLOADED = "video.uploaded";

    private final EventOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public VideoUploadOutboxService(EventOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void appendUploaded(VideoUploadedEvent event) {
        try {
            repository.save(EventOutboxEntity.pending(
                    VIDEO_UPLOADED,
                    event.videoId(),
                    objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化视频上传事件失败", ex);
        }
    }
}
