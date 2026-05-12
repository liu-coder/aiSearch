package com.aisearch.worker.application;

import com.aisearch.worker.domain.VideoSegmentArtifactEntity;
import com.aisearch.worker.domain.VideoSegmentEntity;
import com.aisearch.worker.infrastructure.persistence.VideoSegmentArtifactRepository;
import com.aisearch.worker.infrastructure.persistence.VideoSegmentRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 片段结构化产物服务，把切片计划和片段级证据落到独立表。
 */
@Service
public class SegmentArtifactService {
    private final VideoSegmentRepository segmentRepository;
    private final VideoSegmentArtifactRepository artifactRepository;

    public SegmentArtifactService(VideoSegmentRepository segmentRepository, VideoSegmentArtifactRepository artifactRepository) {
        this.segmentRepository = segmentRepository;
        this.artifactRepository = artifactRepository;
    }

    /**
     * 保存切片计划，重复执行时按 segmentId 幂等更新。
     */
    @Transactional
    public void savePlan(String videoId, MediaProcessingPlan plan) {
        for (VideoSegmentPlan segment : plan.segments()) {
            VideoSegmentEntity entity = segmentRepository.findById(segment.segmentId())
                    .map(existing -> {
                        existing.replace(segment, plan.strategyName());
                        return existing;
                    })
                    .orElseGet(() -> VideoSegmentEntity.of(videoId, segment, plan.strategyName()));
            segmentRepository.save(entity);
        }
    }

    /**
     * 保存片段级产物，重复执行阶段时覆盖旧内容。
     */
    @Transactional
    public void upsert(String videoId, String segmentId, String artifactType, String payload) {
        VideoSegmentArtifactEntity artifact = artifactRepository.findBySegmentIdAndArtifactType(segmentId, artifactType)
                .map(existing -> {
                    existing.replacePayload(payload);
                    return existing;
                })
                .orElseGet(() -> VideoSegmentArtifactEntity.of(videoId, segmentId, artifactType, payload));
        artifactRepository.save(artifact);
    }

    public List<VideoSegmentEntity> segments(String videoId) {
        return segmentRepository.findByVideoIdOrderByStartTimeMsAsc(videoId);
    }

    public SegmentEvidence segmentEvidence(String videoId, String segmentId) {
        VideoSegmentEntity segment = segmentRepository.findById(segmentId)
                .filter(value -> videoId.equals(value.getVideoId()))
                .orElseThrow(() -> new IllegalArgumentException("视频片段不存在: " + videoId + " " + segmentId));
        Map<String, String> artifacts = artifactRepository.findByVideoId(videoId).stream()
                .filter(artifact -> segmentId.equals(artifact.getSegmentId()))
                .collect(Collectors.toMap(VideoSegmentArtifactEntity::getArtifactType, VideoSegmentArtifactEntity::getPayload));
        return new SegmentEvidence(
                videoId,
                segmentId,
                segment.getStartTimeMs(),
                segment.getEndTimeMs(),
                artifacts.getOrDefault("ASR_TEXT", ""),
                artifacts.getOrDefault("OCR_TEXT", ""),
                artifacts.getOrDefault("CAPTION", ""),
                artifacts.getOrDefault("FRAME_URL", ""),
                artifacts);
    }

    public Map<String, Map<String, String>> artifactsBySegment(String videoId) {
        return artifactRepository.findByVideoId(videoId).stream()
                .collect(Collectors.groupingBy(
                        VideoSegmentArtifactEntity::getSegmentId,
                        Collectors.toMap(VideoSegmentArtifactEntity::getArtifactType, VideoSegmentArtifactEntity::getPayload)));
    }

    @Transactional
    public void deleteArtifacts(String videoId, List<String> artifactTypes) {
        artifactRepository.deleteByVideoIdAndArtifactTypeIn(videoId, artifactTypes);
    }

    public record SegmentEvidence(
            String videoId,
            String segmentId,
            long startTimeMs,
            long endTimeMs,
            String asrText,
            String ocrText,
            String caption,
            String keyFrameUrl,
            Map<String, String> artifacts) {
    }
}
