package com.aisearch.model.application;

import jakarta.validation.constraints.NotBlank;

/**
 * 视觉模型请求，imageUrl 指向关键帧或图片对象。
 */
public record VisionRequest(
        @NotBlank String imageUrl,
        String prompt
) {
}
