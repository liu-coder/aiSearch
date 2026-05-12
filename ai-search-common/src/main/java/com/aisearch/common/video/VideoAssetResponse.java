package com.aisearch.common.video;

/**
 * 视频资产创建后的响应，告诉调用方对象存储位置和下一步动作。
 */
public record VideoAssetResponse(
        String videoId,
        String objectKey,
        String bucket,
        String uploadUrl,
        VideoAssetStatus status,
        String nextAction
) {
}
