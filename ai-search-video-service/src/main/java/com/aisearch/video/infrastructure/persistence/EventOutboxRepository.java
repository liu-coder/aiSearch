package com.aisearch.video.infrastructure.persistence;

import com.aisearch.video.domain.EventOutboxEntity;
import com.aisearch.video.domain.EventOutboxStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * outbox 仓储，按状态和下次尝试时间扫描待发布事件。
 */
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {
    List<EventOutboxEntity> findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            EventOutboxStatus status,
            Instant now);
}
