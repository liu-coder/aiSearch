package com.aisearch.model.application;

import jakarta.validation.constraints.NotBlank;

/**
 * 语音识别请求，fileUrl 需要是模型服务可访问的公网或内网 URL。
 */
public record AsrRequest(@NotBlank String fileUrl) {
}
