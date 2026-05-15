package com.aisearch.worker.application;

import com.aisearch.worker.domain.StageTaskStatus;
import com.aisearch.worker.domain.VideoProcessingStageTaskEntity;
import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import com.aisearch.worker.infrastructure.persistence.VideoProcessingStageTaskRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 离线任务执行器，周期性领取 PENDING 阶段任务并推进状态。
 */
@Service
public class VideoProcessingTaskExecutor {
    private final VideoProcessingStageTaskRepository repository;
    private final WorkerPipelineProperties properties;
    private final FailureClassifier failureClassifier;
    private final VideoAssetStatusService videoAssetStatusService;
    private final MeterRegistry meterRegistry;
    private final Map<com.aisearch.common.workflow.WorkflowStage, StageProcessor> processors;

    public VideoProcessingTaskExecutor(
            VideoProcessingStageTaskRepository repository,
            WorkerPipelineProperties properties,
            FailureClassifier failureClassifier,
            VideoAssetStatusService videoAssetStatusService,
            List<StageProcessor> processors,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.properties = properties;
        this.failureClassifier = failureClassifier;
        this.videoAssetStatusService = videoAssetStatusService;
        this.meterRegistry = meterRegistry;
        this.processors = new EnumMap<>(com.aisearch.common.workflow.WorkflowStage.class);
        for (StageProcessor processor : processors) {
            this.processors.put(processor.stage(), processor);
        }
    }

    @Scheduled(fixedDelayString = "${ai-search.worker.poll-delay-ms:5000}")
    public void pollAndExecute() {
        /*
         * 调度轮询流程：
         * 1. 先回收超时 RUNNING 任务，处理 worker 宕机或下游卡死留下的悬挂状态。
         * 2. 再按创建时间读取一批 PENDING 任务，避免后创建任务长期插队。
         * 3. 执行前检查同一 event 的前置阶段是否全部 SUCCEEDED，保证阶段顺序。
         * 4. 对满足条件的任务调用 executeOne，由事务负责状态推进。
         */
        reclaimTimedOutRunningTasks();
        for (VideoProcessingStageTaskEntity task : repository.findTop20ByStatusOrderByCreatedAtAsc(StageTaskStatus.PENDING)) {
            if (repository.previousStagesSucceeded(task.getEventId(), task.getStageSequence())) {
                executeOne(task.getId());
            }
        }
    }

    @Transactional
    public void reclaimTimedOutRunningTasks() {
        /*
         * 超时回收流程：
         * 1. 根据 runningTimeoutMs 计算超时边界。
         * 2. 找出更新时间早于边界的 RUNNING 任务。
         * 3. 未达到最大重试次数则回到 PENDING，等待下一轮重新执行。
         * 4. 达到最大重试次数则标记 FAILED，并保留失败原因供排查。
         */
        Instant deadline = Instant.now().minusMillis(properties.getRunningTimeoutMs());
        for (VideoProcessingStageTaskEntity task : repository
                .findTop20ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(StageTaskStatus.RUNNING, deadline)) {
            if (task.getAttempts() >= properties.getMaxAttempts()) {
                task.markFailed(com.aisearch.worker.domain.StageFailureType.MODEL_TIMEOUT, "任务运行超时且已达到最大重试次数");
                videoAssetStatusService.markFailed(task.getVideoId());
            } else {
                task.markPendingForRetry(com.aisearch.worker.domain.StageFailureType.MODEL_TIMEOUT, "任务运行超时，等待重试");
            }
        }
    }

    @Transactional
    public void executeOne(Long taskId) {
        /*
         * 单任务执行流程：
         * 1. 先用数据库原子 update 把 PENDING 抢占为 RUNNING，避免多 worker 重复执行。
         * 2. 抢占成功后重新读取任务，保证拿到最新 attempts/status。
         * 3. 按 WorkflowStage 找到对应 StageProcessor，没有处理器则直接失败。
         * 4. 处理器成功后标记 SUCCEEDED；失败时按 maxAttempts 决定重试或终止。
         */
        if (repository.claimPendingTask(taskId, Instant.now()) == 0) {
            return;
        }
        VideoProcessingStageTaskEntity task = repository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        StageProcessor processor = processors.get(task.getStage());
        if (processor == null) {
            task.markFailed("未配置阶段处理器: " + task.getStage());
            recordTaskResult(task, "failed", com.aisearch.worker.domain.StageFailureType.CONFIGURATION);
            return;
        }
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            processor.process(task);
            task.markSucceeded();
            recordTaskResult(task, "succeeded", null);
            if (repository.allStagesSucceeded(task.getEventId())) {
                videoAssetStatusService.markReady(task.getVideoId());
            }
        } catch (RuntimeException ex) {
            StageProcessingException classified = failureClassifier.classify(ex);
            if (!classified.retryable() || task.getAttempts() >= properties.getMaxAttempts()) {
                task.markFailed(classified.failureType(), classified.getMessage());
                recordTaskResult(task, "failed", classified.failureType());
                videoAssetStatusService.markFailed(task.getVideoId());
            } else {
                task.markPendingForRetry(classified.failureType(), classified.getMessage());
                recordTaskResult(task, "retry", classified.failureType());
            }
        } finally {
            sample.stop(meterRegistry.timer(
                    "ai_search_worker_stage_task_duration",
                    "stage", task.getStage().name()));
        }
    }

    private void recordTaskResult(VideoProcessingStageTaskEntity task, String result, com.aisearch.worker.domain.StageFailureType failureType) {
        meterRegistry.counter(
                "ai_search_worker_stage_task_total",
                "stage", task.getStage().name(),
                "result", result,
                "failureType", failureType == null ? "none" : failureType.name()).increment();
    }
}
