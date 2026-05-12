package com.aisearch.model.application;

import com.aisearch.common.asr.TimedTextSegment;
import com.aisearch.model.infrastructure.DashScopeModelClient;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 模型网关应用服务，统一暴露 ASR、OCR、Caption、Rerank 和分析能力。
 */
@Service
public class ModelGatewayService {
    private final DashScopeModelClient dashScopeModelClient;

    public ModelGatewayService(DashScopeModelClient dashScopeModelClient) {
        this.dashScopeModelClient = dashScopeModelClient;
    }

    public String transcribe(AsrRequest request) {
        return dashScopeModelClient.transcribe(request.fileUrl());
    }

    public List<TimedTextSegment> transcribeSegments(AsrRequest request) {
        return dashScopeModelClient.transcribeSegments(request.fileUrl());
    }

    public String ocr(VisionRequest request) {
        return dashScopeModelClient.ocr(request.imageUrl(), request.prompt());
    }

    public String caption(VisionRequest request) {
        return dashScopeModelClient.caption(request.imageUrl(), request.prompt());
    }

    public List<RerankResult> rerank(RerankRequest request) {
        return dashScopeModelClient.rerank(request.query(), request.documents(), request.normalizedTopN());
    }

    public String analyze(AnalysisRequest request) {
        return dashScopeModelClient.analyze(request.query(), request.evidence() == null ? List.of() : request.evidence());
    }
}
