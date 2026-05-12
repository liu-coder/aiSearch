package com.aisearch.worker.infrastructure.persistence;

import com.aisearch.worker.domain.VideoAssetReadEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Worker 读取视频资产元数据的仓储。
 */
public interface VideoAssetReadRepository extends JpaRepository<VideoAssetReadEntity, String> {
}
