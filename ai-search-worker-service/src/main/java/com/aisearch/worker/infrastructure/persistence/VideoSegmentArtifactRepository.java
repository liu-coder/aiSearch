package com.aisearch.worker.infrastructure.persistence;

import com.aisearch.worker.domain.VideoSegmentArtifactEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 片段级产物仓储，用于结构化追踪片段 ASR/OCR/Caption/Frame 等证据。
 */
public interface VideoSegmentArtifactRepository extends JpaRepository<VideoSegmentArtifactEntity, Long> {
    Optional<VideoSegmentArtifactEntity> findBySegmentIdAndArtifactType(String segmentId, String artifactType);

    List<VideoSegmentArtifactEntity> findByVideoId(String videoId);

    void deleteByVideoIdAndArtifactTypeIn(String videoId, List<String> artifactTypes);
}
