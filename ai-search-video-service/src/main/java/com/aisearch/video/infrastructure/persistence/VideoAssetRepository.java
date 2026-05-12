package com.aisearch.video.infrastructure.persistence;

import com.aisearch.video.domain.VideoAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 视频资产仓储，当前由 Spring Data JPA 落到 MySQL。
 */
public interface VideoAssetRepository extends JpaRepository<VideoAssetEntity, String> {
}
