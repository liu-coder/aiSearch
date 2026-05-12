package com.aisearch.video.domain;

/**
 * outbox 事件发布状态。
 */
public enum EventOutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED
}
