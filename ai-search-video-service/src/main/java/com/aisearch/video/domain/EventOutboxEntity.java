package com.aisearch.video.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 本地事务事件表，用于保证视频状态更新和 MQ 事件发布最终一致。
 */
@Entity
@Table(name = "event_outbox")
public class EventOutboxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 80)
    private String aggregateId;

    @Lob
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EventOutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EventOutboxEntity() {
    }

    private EventOutboxEntity(String eventType, String aggregateId, String payload) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = EventOutboxStatus.PENDING;
        this.nextAttemptAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static EventOutboxEntity pending(String eventType, String aggregateId, String payload) {
        return new EventOutboxEntity(eventType, aggregateId, payload);
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public int getAttempts() {
        return attempts;
    }

    public void markPublishing() {
        this.status = EventOutboxStatus.PUBLISHING;
        this.attempts += 1;
        this.updatedAt = Instant.now();
    }

    public void markPublished() {
        this.status = EventOutboxStatus.PUBLISHED;
        this.updatedAt = Instant.now();
    }

    public void markPendingRetry(String error, Instant nextAttemptAt) {
        this.status = EventOutboxStatus.PENDING;
        this.lastError = truncate(error);
        this.nextAttemptAt = nextAttemptAt;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = EventOutboxStatus.FAILED;
        this.lastError = truncate(error);
        this.updatedAt = Instant.now();
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.substring(0, Math.min(error.length(), 1000));
    }
}
