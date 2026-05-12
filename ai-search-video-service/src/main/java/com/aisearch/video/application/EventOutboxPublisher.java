package com.aisearch.video.application;

import com.aisearch.video.domain.EventOutboxEntity;
import com.aisearch.video.domain.EventOutboxStatus;
import com.aisearch.video.infrastructure.event.VideoProcessingEventPublisher;
import com.aisearch.video.infrastructure.event.VideoUploadedEvent;
import com.aisearch.video.infrastructure.persistence.EventOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * outbox 发布器，异步扫描待发布事件并投递到 RocketMQ。
 */
@Service
public class EventOutboxPublisher {
    private final EventOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final VideoProcessingEventPublisher eventPublisher;
    private final int maxAttempts;

    public EventOutboxPublisher(
            EventOutboxRepository repository,
            ObjectMapper objectMapper,
            VideoProcessingEventPublisher eventPublisher,
            @Value("${ai-search.outbox.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${ai-search.outbox.poll-delay-ms:3000}")
    public void publishPending() {
        /*
         * outbox 发布流程：
         * 1. 扫描到期的 PENDING 事件，避免接口线程直接阻塞在 MQ 发送上。
         * 2. 单条事件进入事务，先标记 PUBLISHING 并增加 attempts。
         * 3. 根据 eventType 反序列化 payload，再调用具体 MQ publisher。
         * 4. 成功标记 PUBLISHED；失败按重试次数决定回到 PENDING 或标记 FAILED。
         */
        for (EventOutboxEntity event : repository
                .findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(EventOutboxStatus.PENDING, Instant.now())) {
            publishOne(event.getId());
        }
    }

    @Transactional
    public void publishOne(Long eventId) {
        EventOutboxEntity event = repository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("outbox 事件不存在: " + eventId));
        try {
            event.markPublishing();
            if (VideoUploadOutboxService.VIDEO_UPLOADED.equals(event.getEventType())) {
                eventPublisher.publishUploaded(readPayload(event));
            } else {
                throw new IllegalStateException("不支持的 outbox 事件类型: " + event.getEventType());
            }
            event.markPublished();
        } catch (RuntimeException ex) {
            if (event.getAttempts() >= maxAttempts) {
                event.markFailed(ex.getMessage());
            } else {
                event.markPendingRetry(ex.getMessage(), Instant.now().plus(backoff(event.getAttempts())));
            }
        }
    }

    private VideoUploadedEvent readPayload(EventOutboxEntity event) {
        try {
            return objectMapper.readValue(event.getPayload(), VideoUploadedEvent.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("反序列化视频上传事件失败", ex);
        }
    }

    private Duration backoff(int attempts) {
        return Duration.ofSeconds(Math.min(60, Math.max(1, attempts) * 5L));
    }
}
