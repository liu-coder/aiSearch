package com.aisearch.worker.application;

import com.aisearch.worker.domain.VideoAssetReadEntity;
import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import com.aisearch.worker.infrastructure.storage.WorkerObjectStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 基于 FFmpeg 的离线媒体处理服务，负责从原始视频抽取音频和关键帧。
 */
@Service
public class MediaProcessingService {
    private static final Pattern SCENE_TIME_PATTERN = Pattern.compile("pts_time:([0-9.]+)");
    private static final Pattern SILENCE_END_PATTERN = Pattern.compile("silence_end: ([0-9.]+)");

    private final WorkerPipelineProperties properties;
    private final WorkerObjectStorageService objectStorageService;
    private final MediaProcessingStrategy mediaProcessingStrategy;
    private final String ffmpegCommand;
    private final String ffprobeCommand;

    public MediaProcessingService(
            WorkerPipelineProperties properties,
            WorkerObjectStorageService objectStorageService,
            MediaProcessingStrategy mediaProcessingStrategy,
            @Value("${ai-search.ffmpeg.command:ffmpeg}") String ffmpegCommand,
            @Value("${ai-search.ffmpeg.ffprobe-command:ffprobe}") String ffprobeCommand) {
        this.properties = properties;
        this.objectStorageService = objectStorageService;
        this.mediaProcessingStrategy = mediaProcessingStrategy;
        this.ffmpegCommand = ffmpegCommand;
        this.ffprobeCommand = ffprobeCommand;
    }

    /**
     * 抽取音频并上传到处理产物 bucket。
     */
    public MediaArtifact extractAudio(VideoAssetReadEntity asset) {
        Path original = originalVideo(asset);
        Path audio = workspace(asset).resolve("audio.mp3");
        runFfmpeg("-y", "-i", original.toString(), "-vn", "-acodec", "mp3", audio.toString());
        return uploadProcessed(asset, audio, "audio.mp3", "audio/mpeg");
    }

    /**
     * 按动态策略抽取多个关键帧并上传到处理产物 bucket。
     */
    public List<FrameMediaArtifact> extractFrames(VideoAssetReadEntity asset) {
        Path original = originalVideo(asset);
        MediaProcessingPlan plan = processingPlan(asset);
        List<FrameMediaArtifact> frames = new ArrayList<>();
        for (VideoSegmentPlan segment : plan.segments()) {
            String fileName = segment.segmentId() + ".jpg";
            Path frame = workspace(asset).resolve(fileName);
            runFfmpeg("-y", "-ss", seconds(segment.keyFrameTimeMs()), "-i", original.toString(), "-frames:v", "1", frame.toString());
            MediaArtifact uploaded = uploadProcessed(asset, frame, "frames/" + fileName, "image/jpeg");
            frames.add(new FrameMediaArtifact(segment, uploaded.bucket(), uploaded.objectKey(), uploaded.url()));
        }
        return frames;
    }

    /**
     * 生成并返回当前视频的动态处理计划；调用方可将计划持久化为 artifact。
     */
    public MediaProcessingPlan processingPlan(VideoAssetReadEntity asset) {
        Path original = originalVideo(asset);
        long durationMs = probeDurationMs(original);
        List<Long> contentBoundaries = detectContentBoundaries(original);
        return mediaProcessingStrategy.plan(asset, durationMs, contentBoundaries);
    }

    /**
     * 确保原始视频已经落到本地工作目录；重复执行阶段时直接复用已下载文件。
     */
    public Path originalVideo(VideoAssetReadEntity asset) {
        Path original = workspace(asset).resolve(safeFileName(asset.getFileName()));
        if (Files.exists(original)) {
            return original;
        }
        objectStorageService.download(asset.getBucket(), asset.getObjectKey(), original);
        return original;
    }

    private MediaArtifact uploadProcessed(VideoAssetReadEntity asset, Path file, String fileName, String contentType) {
        String bucket = properties.getStorage().getProcessedBucket();
        String objectKey = "processed/" + asset.getVideoId() + "/" + fileName;
        objectStorageService.upload(bucket, objectKey, file, contentType);
        return new MediaArtifact(bucket, objectKey, objectStorageService.presignedGetUrl(bucket, objectKey));
    }

    private Path workspace(VideoAssetReadEntity asset) {
        try {
            Path path = Path.of(properties.getWorkspace(), asset.getVideoId());
            Files.createDirectories(path);
            return path;
        } catch (IOException ex) {
            throw new IllegalStateException("创建媒体处理工作目录失败: " + asset.getVideoId(), ex);
        }
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "original-video";
        }
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private long probeDurationMs(Path original) {
        /*
         * 时长探测步骤：
         * 1. 使用 ffprobe 读取 format.duration，避免依赖上传端手动传时长。
         * 2. 探测失败时返回 -1，由策略层按默认时长兜底。
         * 3. 保持失败可降级，避免个别异常媒体阻断完整离线链路。
         */
        try {
            String output = runProcess(Duration.ofMinutes(1), ffprobeCommand,
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    original.toString());
            double seconds = Double.parseDouble(output.trim());
            return Math.max(1000L, (long) (seconds * 1000L));
        } catch (Exception ex) {
            return -1L;
        }
    }

    private List<Long> detectContentBoundaries(Path original) {
        /*
         * 内容边界检测步骤：
         * 1. 镜头检测：用 FFmpeg scene score 找视觉切换点，适合会议/PPT/短视频转场。
         * 2. 静音检测：用 silencedetect 找语音停顿点，适合课程/访谈/播客类视频。
         * 3. 合并并排序边界，将最终决策交给策略层做最小时长、最大时长和数量控制。
         * 4. 任一检测失败只降级该检测源，不影响整体离线处理。
         */
        WorkerPipelineProperties.MediaStrategy strategy = properties.getMediaStrategy();
        LinkedHashSet<Long> boundaries = new LinkedHashSet<>();
        if (strategy.isSceneDetectionEnabled()) {
            boundaries.addAll(detectSceneBoundaries(original, strategy.getSceneThreshold()));
        }
        if (strategy.isSilenceDetectionEnabled()) {
            boundaries.addAll(detectSilenceBoundaries(original, strategy.getSilenceNoise(), strategy.getSilenceDurationSeconds()));
        }
        return boundaries.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private List<Long> detectSceneBoundaries(Path original, double threshold) {
        try {
            String output = runProcess(Duration.ofMinutes(5), ffmpegCommand,
                    "-i", original.toString(),
                    "-vf", "select='gt(scene," + threshold + ")',showinfo",
                    "-f", "null",
                    "-");
            return parseTimes(output, SCENE_TIME_PATTERN);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Long> detectSilenceBoundaries(Path original, String noise, double silenceDurationSeconds) {
        try {
            String output = runProcess(Duration.ofMinutes(5), ffmpegCommand,
                    "-i", original.toString(),
                    "-af", "silencedetect=noise=" + noise + ":d=" + silenceDurationSeconds,
                    "-f", "null",
                    "-");
            return parseTimes(output, SILENCE_END_PATTERN);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Long> parseTimes(String output, Pattern pattern) {
        List<Long> times = new ArrayList<>();
        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            times.add((long) (Double.parseDouble(matcher.group(1)) * 1000L));
        }
        return times;
    }

    private String seconds(long millis) {
        return String.format(java.util.Locale.ROOT, "%.3f", millis / 1000.0);
    }

    private void runFfmpeg(String... args) {
        /*
         * FFmpeg 执行步骤：
         * 1. 按配置的命令拼装进程参数，兼容本地 ffmpeg 或容器内路径。
         * 2. 合并标准错误与标准输出，避免错误日志丢失。
         * 3. 等待进程结束；非 0 退出码交给任务调度器重试。
         */
        try {
            String[] command = new String[args.length + 1];
            command[0] = ffmpegCommand;
            System.arraycopy(args, 0, command, 1, args.length);
            runProcess(Duration.ofMinutes(10), command);
        } catch (IOException ex) {
            throw new IllegalStateException("启动 FFmpeg 失败，请检查 ai-search.ffmpeg.command 配置", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FFmpeg 执行被中断", ex);
        }
    }

    private String runProcess(Duration timeout, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("媒体命令执行超时: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("媒体命令执行失败，退出码: " + process.exitValue() + ", output=" + output);
        }
        return output;
    }
}
