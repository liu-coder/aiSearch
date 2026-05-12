package com.aisearch.common.api;

import java.time.Instant;

/**
 * 统一 API 响应包装，便于网关、前端和调用方稳定处理成功/失败结果。
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp
) {
    /**
     * 构造成功响应，业务数据放在 data 字段中。
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "OK", Instant.now());
    }

    /**
     * 构造失败响应，当前阶段保留简单 message，后续可扩展错误码和 traceId。
     */
    public static <T> ApiResponse<T> failed(String message) {
        return new ApiResponse<>(false, null, message, Instant.now());
    }
}
