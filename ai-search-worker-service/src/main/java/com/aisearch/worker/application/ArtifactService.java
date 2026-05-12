package com.aisearch.worker.application;

import com.aisearch.worker.domain.VideoProcessingArtifactEntity;
import com.aisearch.worker.infrastructure.persistence.VideoProcessingArtifactRepository;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 阶段产物服务，负责 upsert 中间结果并按视频聚合最终索引材料。
 */
@Service
public class ArtifactService {
    private final VideoProcessingArtifactRepository repository;

    public ArtifactService(VideoProcessingArtifactRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void upsert(String videoId, String artifactType, String payload) {
        VideoProcessingArtifactEntity artifact = repository.findByVideoIdAndArtifactType(videoId, artifactType)
                .map(existing -> {
                    existing.replacePayload(payload);
                    return existing;
                })
                .orElseGet(() -> VideoProcessingArtifactEntity.of(videoId, artifactType, payload));
        repository.save(artifact);
    }

    public Map<String, String> artifacts(String videoId) {
        return repository.findByVideoId(videoId).stream()
                .collect(Collectors.toMap(VideoProcessingArtifactEntity::getArtifactType, VideoProcessingArtifactEntity::getPayload));
    }
}
