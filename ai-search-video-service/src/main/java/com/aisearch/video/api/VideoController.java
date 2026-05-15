package com.aisearch.video.api;

import com.aisearch.common.api.ApiResponse;
import com.aisearch.common.video.CompleteUploadRequest;
import com.aisearch.common.video.InitiateUploadRequest;
import com.aisearch.common.video.VideoAssetResponse;
import com.aisearch.video.application.VideoUploadService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 视频资产接口层，当前先提供初始化上传能力。
 */
@RestController
@RequestMapping("/api/videos")
public class VideoController {
    private final VideoUploadService videoUploadService;

    public VideoController(VideoUploadService videoUploadService) {
        this.videoUploadService = videoUploadService;
    }

    @PostMapping("/uploads")
    public ApiResponse<VideoAssetResponse> initiateUpload(@Valid @RequestBody InitiateUploadRequest request) {
        return ApiResponse.ok(videoUploadService.initiate(request));
    }

    @PostMapping("/{videoId}/complete")
    public ApiResponse<VideoAssetResponse> completeUpload(
            @PathVariable("videoId") String videoId,
            @Valid @RequestBody CompleteUploadRequest request) {
        return ApiResponse.ok(videoUploadService.complete(videoId, request));
    }
}
