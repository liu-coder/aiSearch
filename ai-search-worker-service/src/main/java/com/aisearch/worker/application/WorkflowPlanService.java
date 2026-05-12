package com.aisearch.worker.application;

import com.aisearch.common.workflow.WorkflowStage;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 工作流规划服务，集中定义视频入库的标准处理阶段。
 */
@Service
public class WorkflowPlanService {
    public List<WorkflowStage> videoIndexingStages() {
        return List.of(
                WorkflowStage.UPLOADED,
                WorkflowStage.TRANSCODING,
                WorkflowStage.FRAME_EXTRACTING,
                WorkflowStage.ASR_PROCESSING,
                WorkflowStage.OCR_PROCESSING,
                WorkflowStage.CAPTIONING,
                WorkflowStage.EMBEDDING,
                WorkflowStage.INDEXING,
                WorkflowStage.READY);
    }
}
