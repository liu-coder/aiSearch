package com.aisearch.common.search;

import java.util.List;

/**
 * 辩证分析结果：同时表达正向证据、反向证据、不确定性和结论。
 */
public record DialecticalAnalysis(
        List<String> positiveEvidence,
        List<String> counterEvidence,
        List<String> uncertainties,
        String conclusion
) {
    /**
     * 调用方关闭分析时返回的稳定占位结果，避免前端处理 null。
     */
    public static DialecticalAnalysis skipped() {
        return new DialecticalAnalysis(List.of(), List.of(), List.of("调用方未要求生成分析"), "已返回检索结果，未生成辩证分析。");
    }
}
