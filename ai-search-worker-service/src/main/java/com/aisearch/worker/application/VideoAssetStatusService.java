package com.aisearch.worker.application;

import com.aisearch.worker.infrastructure.persistence.VideoAssetReadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worker 侧视频状态回写服务，负责把离线处理结果同步到 video_asset。
 */
@Service
public class VideoAssetStatusService {
    private final VideoAssetReadRepository repository;

    public VideoAssetStatusService(VideoAssetReadRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void markReady(String videoId) {
        repository.findById(videoId).ifPresent(asset -> {
            asset.markReady();
            repository.save(asset);
        });
    }

    @Transactional
    public void markFailed(String videoId) {
        repository.findById(videoId).ifPresent(asset -> {
            asset.markFailed();
            repository.save(asset);
        });
    }
}
