package com.aisearch.model.api;

import com.aisearch.common.asr.TimedTextSegment;
import com.aisearch.common.api.ApiResponse;
import com.aisearch.model.application.AnalysisRequest;
import com.aisearch.model.application.AsrRequest;
import com.aisearch.model.application.EmbeddingRequest;
import com.aisearch.model.application.EmbeddingService;
import com.aisearch.model.application.ModelGatewayService;
import com.aisearch.model.application.RerankRequest;
import com.aisearch.model.application.RerankResult;
import com.aisearch.model.application.VisionRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型网关接口层，当前提供文本 embedding 调试接口。
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {
    private final EmbeddingService embeddingService;
    private final ModelGatewayService modelGatewayService;

    public ModelController(EmbeddingService embeddingService, ModelGatewayService modelGatewayService) {
        this.embeddingService = embeddingService;
        this.modelGatewayService = modelGatewayService;
    }

    @GetMapping("/embedding")
    public ApiResponse<List<Double>> embedding(@RequestParam String text) {
        return ApiResponse.ok(embeddingService.embedText(text));
    }

    @PostMapping("/embeddings")
    public ApiResponse<List<Double>> embedding(@Valid @RequestBody EmbeddingRequest request) {
        return ApiResponse.ok(embeddingService.embed(request));
    }

    @PostMapping("/image-embedding")
    public ApiResponse<List<Double>> imageEmbedding(@Valid @RequestBody VisionRequest request) {
        return ApiResponse.ok(embeddingService.embed(new EmbeddingRequest(request.imageUrl(), "image")));
    }

    @PostMapping("/asr")
    public ApiResponse<String> asr(@Valid @RequestBody AsrRequest request) {
        return ApiResponse.ok(modelGatewayService.transcribe(request));
    }

    @PostMapping("/asr-segments")
    public ApiResponse<List<TimedTextSegment>> asrSegments(@Valid @RequestBody AsrRequest request) {
        return ApiResponse.ok(modelGatewayService.transcribeSegments(request));
    }

    @PostMapping("/ocr")
    public ApiResponse<String> ocr(@Valid @RequestBody VisionRequest request) {
        return ApiResponse.ok(modelGatewayService.ocr(request));
    }

    @PostMapping("/caption")
    public ApiResponse<String> caption(@Valid @RequestBody VisionRequest request) {
        return ApiResponse.ok(modelGatewayService.caption(request));
    }

    @PostMapping("/rerank")
    public ApiResponse<List<RerankResult>> rerank(@Valid @RequestBody RerankRequest request) {
        return ApiResponse.ok(modelGatewayService.rerank(request));
    }

    @PostMapping("/analysis")
    public ApiResponse<String> analysis(@Valid @RequestBody AnalysisRequest request) {
        return ApiResponse.ok(modelGatewayService.analyze(request));
    }
}
