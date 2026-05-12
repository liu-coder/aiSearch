package com.aisearch.worker.application;

import com.aisearch.common.workflow.WorkflowStage;
import com.aisearch.worker.domain.VideoProcessingArtifactEntity;
import com.aisearch.worker.domain.VideoProcessingStageTaskEntity;
import com.aisearch.worker.infrastructure.persistence.VideoProcessingArtifactRepository;
import com.aisearch.worker.infrastructure.persistence.VideoProcessingStageTaskRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 离线工作流调试服务，提供状态查看、产物查看和人工重跑入口。
 */
@Service
public class WorkflowDebugService {
    private final VideoProcessingStageTaskRepository taskRepository;
    private final VideoProcessingArtifactRepository artifactRepository;
    private final VideoProcessingTaskExecutor executor;
    private final SegmentArtifactService segmentArtifactService;
    private final SearchIndexMaintenanceService indexMaintenanceService;

    public WorkflowDebugService(
            VideoProcessingStageTaskRepository taskRepository,
            VideoProcessingArtifactRepository artifactRepository,
            VideoProcessingTaskExecutor executor,
            SegmentArtifactService segmentArtifactService,
            SearchIndexMaintenanceService indexMaintenanceService) {
        this.taskRepository = taskRepository;
        this.artifactRepository = artifactRepository;
        this.executor = executor;
        this.segmentArtifactService = segmentArtifactService;
        this.indexMaintenanceService = indexMaintenanceService;
    }

    WorkflowDebugService(
            VideoProcessingStageTaskRepository taskRepository,
            VideoProcessingArtifactRepository artifactRepository,
            VideoProcessingTaskExecutor executor) {
        this(taskRepository, artifactRepository, executor, null, null);
    }

    WorkflowDebugService(
            VideoProcessingStageTaskRepository taskRepository,
            VideoProcessingArtifactRepository artifactRepository,
            VideoProcessingTaskExecutor executor,
            SegmentArtifactService segmentArtifactService) {
        this(taskRepository, artifactRepository, executor, segmentArtifactService, null);
    }

    public VideoProcessingStatus status(String videoId) {
        List<VideoProcessingStageTaskEntity> tasks = taskRepository.findByVideoIdOrderByStageSequenceAsc(videoId);
        List<StageStatus> stages = tasks.stream().map(this::toStageStatus).toList();
        String overallStatus = stages.stream()
                .filter(stage -> "FAILED".equals(stage.status()))
                .findFirst()
                .map(stage -> "FAILED")
                .orElseGet(() -> stages.stream().allMatch(stage -> "SUCCEEDED".equals(stage.status())) ? "SUCCEEDED" : "PROCESSING");
        return new VideoProcessingStatus(videoId, overallStatus, stages);
    }

    public VideoSlicePlan slicePlan(String videoId) {
        if (segmentArtifactService == null) {
            return new VideoSlicePlan(videoId, List.of());
        }
        List<SegmentPlanItem> segments = segmentArtifactService.segments(videoId).stream()
                .map(segment -> new SegmentPlanItem(
                        segment.getSegmentId(),
                        segment.getStartTimeMs(),
                        segment.getEndTimeMs()))
                .toList();
        return new VideoSlicePlan(videoId, segments);
    }

    public Map<String, Map<String, String>> segmentArtifacts(String videoId) {
        if (segmentArtifactService == null) {
            return Map.of();
        }
        return segmentArtifactService.artifactsBySegment(videoId);
    }

    public SegmentArtifactService.SegmentEvidence segmentEvidence(String videoId, String segmentId) {
        if (segmentArtifactService == null) {
            throw new IllegalStateException("片段产物服务未初始化");
        }
        return segmentArtifactService.segmentEvidence(videoId, segmentId);
    }

    public Map<String, String> stageArtifacts(String videoId) {
        Map<String, String> result = new LinkedHashMap<>();
        for (VideoProcessingArtifactEntity artifact : artifactRepository.findByVideoId(videoId)) {
            result.put(artifact.getArtifactType(), artifact.getPayload());
        }
        return result;
    }

    @Transactional
    public VideoProcessingStatus rerunStage(String videoId, WorkflowStage stage) {
        resetFromStage(videoId, stage);
        return status(videoId);
    }

    @Transactional
    public VideoProcessingStatus rebuildIndex(String videoId) {
        if (indexMaintenanceService != null) {
            indexMaintenanceService.deleteVideo(videoId);
        }
        resetFromStage(videoId, WorkflowStage.EMBEDDING);
        return status(videoId);
    }

    public void deleteIndex(String videoId) {
        if (indexMaintenanceService == null) {
            throw new IllegalStateException("索引维护服务未初始化");
        }
        indexMaintenanceService.deleteVideo(videoId);
    }

    private void resetFromStage(String videoId, WorkflowStage stage) {
        List<VideoProcessingStageTaskEntity> tasks = taskRepository.findByVideoIdOrderByStageSequenceAsc(videoId);
        int sequence = tasks.stream()
                .filter(task -> task.getStage() == stage)
                .findFirst()
                .map(VideoProcessingStageTaskEntity::getStageSequence)
                .orElseThrow(() -> new IllegalArgumentException("视频未找到指定阶段: " + videoId + " " + stage));
        for (VideoProcessingStageTaskEntity task : tasks) {
            if (task.getStageSequence() >= sequence) {
                task.resetForRerun();
            }
        }
        List<String> artifactTypes = artifactTypesFrom(stage);
        artifactRepository.deleteByVideoIdAndArtifactTypeIn(videoId, artifactTypes);
        if (segmentArtifactService != null) {
            segmentArtifactService.deleteArtifacts(videoId, artifactTypes);
        }
    }

    private List<String> artifactTypesFrom(WorkflowStage stage) {
        List<String> types = new ArrayList<>();
        if (stage.ordinal() <= WorkflowStage.ASR_PROCESSING.ordinal()) {
            types.addAll(List.of("ASR_SEGMENTS", "ASR_TEXT"));
        }
        if (stage.ordinal() <= WorkflowStage.OCR_PROCESSING.ordinal()) {
            types.add("OCR_TEXT");
        }
        if (stage.ordinal() <= WorkflowStage.CAPTIONING.ordinal()) {
            types.add("CAPTION");
        }
        if (stage.ordinal() <= WorkflowStage.EMBEDDING.ordinal()) {
            types.add("EMBEDDING_TEXT");
        }
        if (stage.ordinal() <= WorkflowStage.INDEXING.ordinal()) {
            types.add("INDEX_VERSION");
            types.add("INDEX_TEXT");
            types.add("IMAGE_EMBEDDING");
        }
        types.add("READY");
        return types.stream().distinct().toList();
    }

    private StageStatus toStageStatus(VideoProcessingStageTaskEntity task) {
        return new StageStatus(
                task.getStage(),
                task.getStatus().name(),
                task.getStageSequence(),
                task.getAttempts(),
                task.getFailureReason(),
                task.getUpdatedAt().toString());
    }

    public record VideoProcessingStatus(String videoId, String status, List<StageStatus> stages) {
    }

    public record StageStatus(
            WorkflowStage stage,
            String status,
            int sequence,
            int attempts,
            String failureReason,
            String updatedAt) {
    }

    public record VideoSlicePlan(String videoId, List<SegmentPlanItem> segments) {
    }

    public record SegmentPlanItem(String segmentId, long startTimeMs, long endTimeMs) {
    }
}
