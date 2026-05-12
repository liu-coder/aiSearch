package com.aisearch.common.video;

/**
 * 视频资产对外状态，隐藏内部更细的离线处理阶段。
 */
public enum VideoAssetStatus {
    INITIATED,
    UPLOADED,
    PROCESSING,
    READY,
    FAILED
}
