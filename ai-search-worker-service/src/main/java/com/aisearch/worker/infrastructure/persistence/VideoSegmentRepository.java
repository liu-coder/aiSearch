package com.aisearch.worker.infrastructure.persistence;

import com.aisearch.worker.domain.VideoSegmentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 视频片段仓储，支持按视频查询结构化切片结果。
 */
public interface VideoSegmentRepository extends JpaRepository<VideoSegmentEntity, String> {
    List<VideoSegmentEntity> findByVideoIdOrderByStartTimeMsAsc(String videoId);
}
