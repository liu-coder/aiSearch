package com.aisearch.worker.infrastructure.persistence;

import com.aisearch.worker.domain.VideoProcessingArtifactEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 阶段产物仓储，用于生成最终 ES/Milvus 索引文档。
 */
public interface VideoProcessingArtifactRepository extends JpaRepository<VideoProcessingArtifactEntity, Long> {
    Optional<VideoProcessingArtifactEntity> findByVideoIdAndArtifactType(String videoId, String artifactType);

    List<VideoProcessingArtifactEntity> findByVideoId(String videoId);

    void deleteByVideoIdAndArtifactTypeIn(String videoId, List<String> artifactTypes);
}
