package com.aisearch.worker.api;

import com.aisearch.common.api.ApiResponse;
import com.aisearch.common.workflow.WorkflowStage;
import com.aisearch.worker.application.WorkflowDebugService;
import com.aisearch.worker.application.SegmentArtifactService;
import com.aisearch.worker.application.VideoProcessingStatusService;
import com.aisearch.worker.application.WorkflowPlanService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流接口层，用于查看离线视频索引流程的阶段定义。
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowPlanService workflowPlanService;
    private final VideoProcessingStatusService statusService;
    private final WorkflowDebugService debugService;

    public WorkflowController(
            WorkflowPlanService workflowPlanService,
            VideoProcessingStatusService statusService,
            WorkflowDebugService debugService) {
        this.workflowPlanService = workflowPlanService;
        this.statusService = statusService;
        this.debugService = debugService;
    }

    @GetMapping("/video-indexing/stages")
    public ApiResponse<List<WorkflowStage>> stages() {
        return ApiResponse.ok(workflowPlanService.videoIndexingStages());
    }

    @GetMapping("/video-indexing/videos/{videoId}")
    public ApiResponse<VideoProcessingStatusResponse> videoStatus(@PathVariable String videoId) {
        return ApiResponse.ok(statusService.status(videoId));
    }

    @GetMapping("/video-indexing/videos/{videoId}/slice-plan")
    public ApiResponse<WorkflowDebugService.VideoSlicePlan> slicePlan(@PathVariable String videoId) {
        return ApiResponse.ok(debugService.slicePlan(videoId));
    }

    @GetMapping("/video-indexing/videos/{videoId}/artifacts")
    public ApiResponse<Map<String, String>> stageArtifacts(@PathVariable String videoId) {
        return ApiResponse.ok(debugService.stageArtifacts(videoId));
    }

    @GetMapping("/video-indexing/videos/{videoId}/segments/artifacts")
    public ApiResponse<Map<String, Map<String, String>>> segmentArtifacts(@PathVariable String videoId) {
        return ApiResponse.ok(debugService.segmentArtifacts(videoId));
    }

    @GetMapping("/video-indexing/videos/{videoId}/segments/{segmentId}")
    public ApiResponse<SegmentArtifactService.SegmentEvidence> segmentEvidence(
            @PathVariable String videoId,
            @PathVariable String segmentId) {
        return ApiResponse.ok(debugService.segmentEvidence(videoId, segmentId));
    }

    @PostMapping("/video-indexing/videos/{videoId}/stages/{stage}/rerun")
    public ApiResponse<Void> rerunStage(@PathVariable String videoId, @PathVariable WorkflowStage stage) {
        debugService.rerunStage(videoId, stage);
        return ApiResponse.ok(null);
    }

    @PostMapping("/video-indexing/videos/{videoId}/rebuild-index")
    public ApiResponse<Void> rebuildIndex(@PathVariable String videoId) {
        debugService.rebuildIndex(videoId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/video-indexing/videos/{videoId}/delete-index")
    public ApiResponse<Void> deleteIndex(@PathVariable String videoId) {
        debugService.deleteIndex(videoId);
        return ApiResponse.ok(null);
    }
}
