package com.aisearch.common.video;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 初始化上传请求。当前阶段返回对象存储信息，后续会扩展为预签名上传 URL。
 */
public record InitiateUploadRequest(
        @NotBlank @Size(max = 200) String fileName,
        @Positive long fileSize,
        @Size(max = 100) String contentType,
        @Size(max = 200) String title
) {
}
