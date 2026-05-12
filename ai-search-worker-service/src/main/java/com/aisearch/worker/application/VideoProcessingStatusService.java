package com.aisearch.worker.application;

import com.aisearch.worker.api.SegmentView;
import com.aisearch.worker.api.StageTaskView;
import com.aisearch.worker.api.VideoProcessingStatusResponse;
import com.aisearch.worker.domain.VideoSegmentArtifactEntity;
import com.aisearch.worker.infrastructure.persistence.VideoProcessingStageTaskRepository;
import com.aisearch.worker.infrastructure.persistence.VideoSegmentArtifactRepository;
import com.aisearch.worker.infrastructure.persistence.VideoSegmentRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 视频处理状态查询服务，为端到端联调和排障提供统一视图。
 */
@Service
public class VideoProcessingStatusService {
    private final VideoProcessingStageTaskRepository taskRepository;
    private final VideoSegmentRepository segmentRepository;
    private final VideoSegmentArtifactRepository artifactRepository;

    public VideoProcessingStatusService(
            VideoProcessingStageTaskRepository taskRepository,
            VideoSegmentRepository segmentRepository,
            VideoSegmentArtifactRepository artifactRepository) {
        this.taskRepository = taskRepository;
        this.segmentRepository = segmentRepository;
        this.artifactRepository = artifactRepository;
    }

    public VideoProcessingStatusResponse status(String videoId) {
        List<StageTaskView> stages = taskRepository.findByVideoIdOrderByStageSequenceAsc(videoId).stream()
                .map(task -> new StageTaskView(
                        task.getStage(),
                        task.getStatus(),
                        task.getStageSequence(),
                        task.getAttempts(),
                        task.getFailureType(),
                        task.getFailureReason()))
                .toList();
        List<SegmentView> segments = segmentRepository.findByVideoIdOrderByStartTimeMsAsc(videoId).stream()
                .map(segment -> new SegmentView(segment.getSegmentId(), segment.getStartTimeMs(), segment.getEndTimeMs()))
                .toList();
        Map<String, List<String>> artifacts = artifactRepository.findByVideoId(videoId).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        VideoSegmentArtifactEntity::getSegmentId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(VideoSegmentArtifactEntity::getArtifactType, java.util.stream.Collectors.toList())));
        return new VideoProcessingStatusResponse(videoId, stages, segments, artifacts);
    }
}
