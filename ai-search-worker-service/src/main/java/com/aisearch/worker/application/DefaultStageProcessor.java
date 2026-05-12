package com.aisearch.worker.application;

import com.aisearch.common.asr.TimedTextSegment;
import com.aisearch.common.workflow.WorkflowStage;
import com.aisearch.worker.domain.VideoAssetReadEntity;
import com.aisearch.worker.domain.VideoProcessingStageTaskEntity;
import com.aisearch.worker.infrastructure.model.ModelGatewayClient;
import com.aisearch.worker.infrastructure.persistence.VideoAssetReadRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 默认阶段处理器，完成最小可运行离线链路；专业模型处理器接入后可按阶段替换。
 */
public class DefaultStageProcessor implements StageProcessor {
    private final WorkflowStage stage;
    private final ArtifactService artifactService;
    private final VideoAssetReadRepository assetRepository;
    private final MediaProcessingService mediaProcessingService;
    private final ModelGatewayClient modelGatewayClient;
    private final SegmentArtifactService segmentArtifactService;
    private final SearchIndexWriter searchIndexWriter;
    private final VectorIndexWriter vectorIndexWriter;

    public DefaultStageProcessor(
            ArtifactService artifactService,
            VideoAssetReadRepository assetRepository,
            MediaProcessingService mediaProcessingService,
            ModelGatewayClient modelGatewayClient,
            SegmentArtifactService segmentArtifactService,
            SearchIndexWriter searchIndexWriter,
            VectorIndexWriter vectorIndexWriter) {
        this(WorkflowStage.TRANSCODING, artifactService, assetRepository, mediaProcessingService, modelGatewayClient, segmentArtifactService, searchIndexWriter, vectorIndexWriter);
    }

    private DefaultStageProcessor(
            WorkflowStage stage,
            ArtifactService artifactService,
            VideoAssetReadRepository assetRepository,
            MediaProcessingService mediaProcessingService,
            ModelGatewayClient modelGatewayClient,
            SegmentArtifactService segmentArtifactService,
            SearchIndexWriter searchIndexWriter,
            VectorIndexWriter vectorIndexWriter) {
        this.stage = stage;
        this.artifactService = artifactService;
        this.assetRepository = assetRepository;
        this.mediaProcessingService = mediaProcessingService;
        this.modelGatewayClient = modelGatewayClient;
        this.segmentArtifactService = segmentArtifactService;
        this.searchIndexWriter = searchIndexWriter;
        this.vectorIndexWriter = vectorIndexWriter;
    }

    @Override
    public WorkflowStage stage() {
        return stage;
    }

    @Override
    public void process(VideoProcessingStageTaskEntity task) {
        /*
         * 阶段处理流程：
         * 1. 读取 video-service 写入的 video_asset，只读复用上传阶段的资产事实。
         * 2. TRANSCODING 先确认原始文件可下载到本地工作目录。
         * 3. FRAME/ASR/OCR/CAPTION 分别生成媒体产物并调用模型服务。
         * 4. EMBEDDING/INDEXING 按 segment 时间范围聚合 ASR/OCR/Caption，生成向量并写入 ES/Milvus。
         * 5. READY 阶段写入完成标记，便于后续接口展示视频可检索状态。
         */
        VideoAssetReadEntity asset = assetRepository.findById(task.getVideoId())
                .orElseThrow(() -> new IllegalStateException("视频资产不存在: " + task.getVideoId()));
        switch (task.getStage()) {
            case TRANSCODING -> transcode(asset);
            case FRAME_EXTRACTING -> extractFrame(task, asset);
            case ASR_PROCESSING -> asr(task, asset);
            case OCR_PROCESSING -> ocr(task, asset);
            case CAPTIONING -> caption(task, asset);
            case EMBEDDING -> artifactService.upsert(task.getVideoId(), "EMBEDDING_TEXT", embeddingText(asset, artifactService.artifacts(task.getVideoId())));
            case INDEXING -> index(task, asset);
            case READY -> artifactService.upsert(task.getVideoId(), "READY", "true");
            default -> throw new IllegalStateException("不支持的默认阶段: " + task.getStage());
        }
    }

    private void transcode(VideoAssetReadEntity asset) {
        /*
         * 当前阶段不做格式转码，只验证原始视频已经能从 MinIO 下载。
         * 后续需要多码率播放时，可在这里扩展 HLS/MP4 转码产物。
         */
        mediaProcessingService.originalVideo(asset);
        artifactService.upsert(asset.getVideoId(), "TRANSCODED_OBJECT", asset.getObjectKey());
    }

    private void extractFrame(VideoProcessingStageTaskEntity task, VideoAssetReadEntity asset) {
        MediaProcessingPlan plan = mediaProcessingService.processingPlan(asset);
        List<FrameMediaArtifact> frames = mediaProcessingService.extractFrames(asset);
        segmentArtifactService.savePlan(task.getVideoId(), plan);
        artifactService.upsert(task.getVideoId(), "PROCESSING_PLAN", serializePlan(plan));
        artifactService.upsert(task.getVideoId(), "FRAME_MANIFEST", serializeFrames(frames));
        for (FrameMediaArtifact frame : frames) {
            segmentArtifactService.upsert(task.getVideoId(), frame.segment().segmentId(), "FRAME_URL", frame.url());
            segmentArtifactService.upsert(task.getVideoId(), frame.segment().segmentId(), "FRAME_OBJECT", frame.objectKey());
        }
    }

    private void asr(VideoProcessingStageTaskEntity task, VideoAssetReadEntity asset) {
        /*
         * ASR 步骤：
         * 1. 从视频抽取音频并上传到处理产物 bucket。
         * 2. 将临时 URL 传给 model-service 的 DashScope ASR 能力。
         * 3. 保存音频对象和带时间戳的句子级 ASR，供片段级索引精确对齐。
         */
        MediaArtifact audio = mediaProcessingService.extractAudio(asset);
        artifactService.upsert(task.getVideoId(), "AUDIO_OBJECT", audio.objectKey());
        artifactService.upsert(task.getVideoId(), "AUDIO_URL", audio.url());
        List<TimedTextSegment> segments = safeAsrSegments(task.getVideoId(), audio.url());
        artifactService.upsert(task.getVideoId(), "ASR_SEGMENTS", serializeAsrSegments(segments));
        artifactService.upsert(task.getVideoId(), "ASR_TEXT", joinAsrText(segments));
        for (FrameRecord frame : frameRecords(task.getVideoId(), asset)) {
            segmentArtifactService.upsert(task.getVideoId(), frame.segmentId(), "ASR_TEXT",
                    TimedTextAligner.align(segments, frame.startTimeMs(), frame.endTimeMs(), ""));
        }
    }

    private void ocr(VideoProcessingStageTaskEntity task, VideoAssetReadEntity asset) {
        List<FrameRecord> frames = frameRecords(task.getVideoId(), asset);
        List<String> ocrTexts = new ArrayList<>();
        for (FrameRecord frame : frames) {
            String ocr = safeVisionText(task.getVideoId(), "OCR", frame.segmentId(), () -> modelGatewayClient.ocr(frame.url()));
            ocrTexts.add(frame.segmentId() + ": " + ocr);
            segmentArtifactService.upsert(task.getVideoId(), frame.segmentId(), "OCR_TEXT", ocr);
        }
        artifactService.upsert(task.getVideoId(), "OCR_TEXT", String.join("\n", ocrTexts));
    }

    private void caption(VideoProcessingStageTaskEntity task, VideoAssetReadEntity asset) {
        List<FrameRecord> frames = frameRecords(task.getVideoId(), asset);
        List<String> captions = new ArrayList<>();
        for (FrameRecord frame : frames) {
            String caption = safeVisionText(task.getVideoId(), "CAPTION", frame.segmentId(), () -> modelGatewayClient.caption(frame.url()));
            captions.add(frame.segmentId() + ": " + caption);
            segmentArtifactService.upsert(task.getVideoId(), frame.segmentId(), "CAPTION", caption);
        }
        artifactService.upsert(task.getVideoId(), "CAPTION", String.join("\n", captions));
    }

    private void index(VideoProcessingStageTaskEntity task, VideoAssetReadEntity asset) {
        /*
         * 索引写入流程：
         * 1. 聚合当前视频已生成的 ASR/OCR/Caption 等阶段产物。
         * 2. 构造一个片段级 IndexSegmentDocument，后续真实切片会扩展为多片段。
         * 3. 基于标题和文本产物生成 embedding，保证文本召回和向量召回语义一致。
         * 4. 先写 Elasticsearch 倒排索引，再写 Milvus 向量索引；任一失败都会触发任务重试。
         */
        Map<String, String> artifacts = artifactService.artifacts(task.getVideoId());
        String asr = artifacts.getOrDefault("ASR_TEXT", "");
        String ocr = artifacts.getOrDefault("OCR_TEXT", "");
        String caption = artifacts.getOrDefault("CAPTION", "");
        List<TimedTextSegment> asrSegments = parseAsrSegments(artifacts.get("ASR_SEGMENTS"));
        List<FrameRecord> frames = parseFrames(artifacts.get("FRAME_MANIFEST"));
        if (frames.isEmpty()) {
            frames = List.of(new FrameRecord(task.getVideoId() + "-seg-0001", 0, 0, ""));
        }
        for (FrameRecord frame : frames) {
            String segmentAsr = segmentAsrText(asrSegments, frame, asr);
            String segmentOcr = segmentText(ocr, frame.segmentId());
            String segmentCaption = segmentText(caption, frame.segmentId());
            String text = String.join(" ", List.of(asset.getTitle(), segmentAsr, segmentOcr, segmentCaption));
            segmentArtifactService.upsert(task.getVideoId(), frame.segmentId(), "INDEX_TEXT", text);
            List<Double> embedding = modelGatewayClient.embedding(text);
            List<Double> imageEmbedding = frame.url().isBlank() ? embedding : modelGatewayClient.imageEmbedding(frame.url());
            segmentArtifactService.upsert(task.getVideoId(), frame.segmentId(), "IMAGE_EMBEDDING", "dimension=" + imageEmbedding.size());
            IndexSegmentDocument document = new IndexSegmentDocument(
                    task.getVideoId(),
                    frame.segmentId(),
                    asset.getTitle(),
                    frame.startTimeMs(),
                    frame.endTimeMs(),
                    segmentAsr,
                    segmentOcr,
                    segmentCaption,
                    embedding,
                    imageEmbedding);
            searchIndexWriter.index(document);
            vectorIndexWriter.upsert(document);
        }
    }

    private List<FrameRecord> frameRecords(String videoId, VideoAssetReadEntity asset) {
        Map<String, String> artifacts = artifactService.artifacts(videoId);
        List<FrameRecord> existing = parseFrames(artifacts.get("FRAME_MANIFEST"));
        if (!existing.isEmpty()) {
            return existing;
        }
        MediaProcessingPlan plan = mediaProcessingService.processingPlan(asset);
        List<FrameMediaArtifact> frames = mediaProcessingService.extractFrames(asset);
        segmentArtifactService.savePlan(videoId, plan);
        artifactService.upsert(videoId, "PROCESSING_PLAN", serializePlan(plan));
        artifactService.upsert(videoId, "FRAME_MANIFEST", serializeFrames(frames));
        for (FrameMediaArtifact frame : frames) {
            segmentArtifactService.upsert(videoId, frame.segment().segmentId(), "FRAME_URL", frame.url());
            segmentArtifactService.upsert(videoId, frame.segment().segmentId(), "FRAME_OBJECT", frame.objectKey());
        }
        return parseFrames(serializeFrames(frames));
    }

    private String embeddingText(VideoAssetReadEntity asset, Map<String, String> artifacts) {
        return String.join(" ", List.of(
                asset.getTitle(),
                asset.getFileName(),
                asset.getContentType() == null ? "" : asset.getContentType(),
                artifacts.getOrDefault("ASR_SEGMENTS", artifacts.getOrDefault("ASR_TEXT", "")),
                artifacts.getOrDefault("OCR_TEXT", ""),
                artifacts.getOrDefault("CAPTION", "")));
    }

    private String serializePlan(MediaProcessingPlan plan) {
        return "strategy=" + plan.strategyName()
                + ", durationMs=" + plan.durationMs()
                + ", segmentDurationMs=" + plan.segmentDurationMs()
                + ", segments=" + plan.segments().size();
    }

    private String serializeFrames(List<FrameMediaArtifact> frames) {
        return frames.stream()
                .map(frame -> frame.segment().segmentId()
                        + "|" + frame.segment().startTimeMs()
                        + "|" + frame.segment().endTimeMs()
                        + "|" + frame.url())
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
    }

    private List<FrameRecord> parseFrames(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        List<FrameRecord> frames = new ArrayList<>();
        for (String line : payload.split("\\R")) {
            String[] parts = line.split("\\|", 4);
            if (parts.length == 4) {
                frames.add(new FrameRecord(parts[0], Long.parseLong(parts[1]), Long.parseLong(parts[2]), parts[3]));
            }
        }
        return frames;
    }

    private String segmentText(String multiline, String segmentId) {
        if (multiline == null || multiline.isBlank()) {
            return "";
        }
        for (String line : multiline.split("\\R")) {
            if (line.startsWith(segmentId + ":")) {
                return line.substring((segmentId + ":").length()).trim();
            }
        }
        return multiline;
    }

    private String serializeAsrSegments(List<TimedTextSegment> segments) {
        return segments.stream()
                .map(segment -> segment.startTimeMs()
                        + "|" + segment.endTimeMs()
                        + "|" + (segment.speakerId() == null ? "" : segment.speakerId())
                        + "|" + sanitize(segment.text()))
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
    }

    private List<TimedTextSegment> parseAsrSegments(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        List<TimedTextSegment> segments = new ArrayList<>();
        for (String line : payload.split("\\R")) {
            String[] parts = line.split("\\|", 4);
            if (parts.length == 4) {
                Integer speakerId = parts[2].isBlank() ? null : Integer.parseInt(parts[2]);
                segments.add(new TimedTextSegment(Long.parseLong(parts[0]), Long.parseLong(parts[1]), parts[3], speakerId));
            }
        }
        return segments;
    }

    private String segmentAsrText(List<TimedTextSegment> asrSegments, FrameRecord frame, String fallbackAsr) {
        return TimedTextAligner.align(asrSegments, frame.startTimeMs(), frame.endTimeMs(), fallbackAsr);
    }

    private String joinAsrText(List<TimedTextSegment> segments) {
        return segments.stream()
                .map(TimedTextSegment::text)
                .filter(value -> value != null && !value.isBlank())
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
    }

    private String sanitize(String text) {
        return text == null ? "" : text.replace("\n", " ").replace("|", " ");
    }

    private List<TimedTextSegment> safeAsrSegments(String videoId, String audioUrl) {
        try {
            return modelGatewayClient.asrSegments(audioUrl);
        } catch (RuntimeException ex) {
            artifactService.upsert(videoId, "MODEL_DEGRADATION",
                    "ASR_PROCESSING degraded: " + sanitize(ex.getMessage()));
            return List.of();
        }
    }

    private String safeVisionText(String videoId, String stage, String segmentId, VisionCall call) {
        try {
            return call.get();
        } catch (RuntimeException ex) {
            artifactService.upsert(videoId, "MODEL_DEGRADATION",
                    stage + " degraded at " + segmentId + ": " + sanitize(ex.getMessage()));
            return "";
        }
    }

    @FunctionalInterface
    private interface VisionCall {
        String get();
    }

    private record FrameRecord(String segmentId, long startTimeMs, long endTimeMs, String url) {
    }
}
