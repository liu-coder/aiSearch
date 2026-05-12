package com.aisearch.search.infrastructure;

import com.aisearch.common.search.RecallSource;
import com.aisearch.search.domain.CandidateSegment;
import java.util.List;
import java.util.Map;

/**
 * 把 ES/Milvus 返回的松散字段映射成统一候选片段，字段别名兼容 snake_case 和 camelCase。
 */
final class SearchDocumentMapper {
    private SearchDocumentMapper() {
    }

    static CandidateSegment toCandidate(Map<String, Object> document, double score, RecallSource source) {
        return new CandidateSegment(
                stringValue(document, "videoId", "video_id"),
                stringValue(document, "segmentId", "segment_id", "id"),
                stringValue(document, "title", "videoTitle", "video_title"),
                longValue(document, "startTimeMs", "start_time_ms", "startMs"),
                longValue(document, "endTimeMs", "end_time_ms", "endMs"),
                score,
                List.of(source));
    }

    private static String stringValue(Map<String, Object> document, String... keys) {
        Object value = value(document, keys);
        return value == null ? "" : value.toString();
    }

    private static long longValue(Map<String, Object> document, String... keys) {
        Object value = value(document, keys);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(value.toString());
    }

    private static Object value(Map<String, Object> document, String... keys) {
        for (String key : keys) {
            if (document.containsKey(key)) {
                return document.get(key);
            }
        }
        return null;
    }
}
