package com.aisearch.model.infrastructure;

import com.aisearch.common.asr.TimedTextSegment;
import com.aisearch.model.application.RerankResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 阿里云百炼模型客户端，封装 ASR、OCR、视觉描述、文本精排和辩证分析协议。
 */
@Component
public class DashScopeModelClient {
    private final RestClient restClient;
    private final ModelProviderProperties properties;

    public DashScopeModelClient(RestClient.Builder builder, ModelProviderProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public String transcribe(String fileUrl) {
        return transcribeSegments(fileUrl).stream()
                .map(TimedTextSegment::text)
                .filter(value -> value != null && !value.isBlank())
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
    }

    @SuppressWarnings("unchecked")
    public List<TimedTextSegment> transcribeSegments(String fileUrl) {
        /*
         * ASR 调用流程：
         * 1. 提交 paraformer-v2 异步转写任务，fileUrl 必须可被 DashScope 访问。
         * 2. 从 output.task_id 取任务 ID。
         * 3. 轮询任务查询接口，直到 SUCCEEDED/FAILED 或达到轮询上限。
         * 4. 读取 transcription_url 指向的 JSON 结果，提取 sentences 的 begin_time/end_time/text。
         */
        Map<String, Object> submit = restClient.post()
                .uri(properties.getDashscope().getAsrSubmitEndpoint())
                .headers(headers -> {
                    headers.setBearerAuth(requiredApiKey());
                    headers.add("X-DashScope-Async", "enable");
                })
                .body(Map.of(
                        "model", properties.getDashscope().getAsrModel(),
                        "input", Map.of("file_urls", List.of(fileUrl))))
                .retrieve()
                .body(Map.class);
        String taskId = stringValue((Map<String, Object>) submit.get("output"), "task_id", "taskId");
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalStateException("DashScope ASR 未返回 task_id");
        }
        return pollAsrResult(taskId);
    }

    @SuppressWarnings("unchecked")
    private List<TimedTextSegment> pollAsrResult(String taskId) {
        for (int i = 0; i < 60; i++) {
            Map<String, Object> response = restClient.get()
                    .uri(properties.getDashscope().getTaskEndpoint(), taskId)
                    .headers(headers -> headers.setBearerAuth(requiredApiKey()))
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> output = (Map<String, Object>) response.getOrDefault("output", Map.of());
            String status = stringValue(output, "task_status", "taskStatus");
            if ("SUCCEEDED".equalsIgnoreCase(status)) {
                return extractAsrSegments(output);
            }
            if ("FAILED".equalsIgnoreCase(status)) {
                throw new IllegalStateException("DashScope ASR 任务失败: " + output);
            }
            sleep(Duration.ofSeconds(2));
        }
        throw new IllegalStateException("DashScope ASR 任务轮询超时: " + taskId);
    }

    public String ocr(String imageUrl, String prompt) {
        return visionChat(properties.getDashscope().getOcrModel(), imageUrl,
                prompt == null || prompt.isBlank() ? "请提取图片中的全部可见文字，保持原文。" : prompt);
    }

    public String caption(String imageUrl, String prompt) {
        return visionChat(properties.getDashscope().getCaptionModel(), imageUrl,
                prompt == null || prompt.isBlank() ? "请用中文描述这张视频关键帧中的主体、场景、动作和重要细节。" : prompt);
    }

    public String analyze(String query, List<String> evidence) {
        String prompt = "请基于检索证据进行辩证分析，分别给出支持点、限制点和结论。\n查询：" + query
                + "\n证据：\n" + String.join("\n", evidence);
        return textChat(properties.getDashscope().getAnalysisModel(), prompt);
    }

    @SuppressWarnings("unchecked")
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        Map<String, Object> response = restClient.post()
                .uri(properties.getDashscope().getRerankEndpoint())
                .headers(headers -> headers.setBearerAuth(requiredApiKey()))
                .body(Map.of(
                        "model", properties.getDashscope().getRerankModel(),
                        "input", Map.of("query", query, "documents", documents),
                        "parameters", Map.of("return_documents", true, "top_n", topN)))
                .retrieve()
                .body(Map.class);
        Map<String, Object> output = (Map<String, Object>) response.getOrDefault("output", Map.of());
        List<Map<String, Object>> results = (List<Map<String, Object>>) output.getOrDefault("results", List.of());
        List<RerankResult> reranked = new ArrayList<>();
        for (Map<String, Object> result : results) {
            int index = ((Number) result.getOrDefault("index", 0)).intValue();
            double score = ((Number) result.getOrDefault("relevance_score", result.getOrDefault("score", 0.0))).doubleValue();
            String document = result.getOrDefault("document", documents.get(index)).toString();
            reranked.add(new RerankResult(index, score, document));
        }
        return reranked;
    }

    @SuppressWarnings("unchecked")
    private String visionChat(String model, String imageUrl, String prompt) {
        Map<String, Object> response = restClient.post()
                .uri(properties.getDashscope().getChatEndpoint())
                .headers(headers -> headers.setBearerAuth(requiredApiKey()))
                .body(Map.of(
                        "model", model,
                        "messages", List.of(Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)),
                                        Map.of("type", "text", "text", prompt))))))
                .retrieve()
                .body(Map.class);
        return extractChatText(response);
    }

    private String textChat(String model, String prompt) {
        Map<String, Object> response = restClient.post()
                .uri(properties.getDashscope().getChatEndpoint())
                .headers(headers -> headers.setBearerAuth(requiredApiKey()))
                .body(Map.of(
                        "model", model,
                        "messages", List.of(Map.of("role", "user", "content", prompt))))
                .retrieve()
                .body(Map.class);
        return extractChatText(response);
    }

    @SuppressWarnings("unchecked")
    private String extractChatText(Map<String, Object> response) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getOrDefault("choices", List.of());
        if (choices.isEmpty()) {
            throw new IllegalStateException("DashScope chat 返回缺少 choices");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).getOrDefault("message", Map.of());
        Object content = message.get("content");
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof List<?> parts) {
            return parts.stream().map(Object::toString).reduce("", String::concat);
        }
        throw new IllegalStateException("DashScope chat 返回缺少 content");
    }

    @SuppressWarnings("unchecked")
    private List<TimedTextSegment> extractAsrSegments(Map<String, Object> output) {
        Object transcriptionUrl = output.get("transcription_url");
        if (transcriptionUrl != null && !transcriptionUrl.toString().isBlank()) {
            Map<String, Object> transcription = restClient.get()
                    .uri(transcriptionUrl.toString())
                    .retrieve()
                    .body(Map.class);
            List<TimedTextSegment> segments = extractSegmentsFromTranscription(transcription);
            if (!segments.isEmpty()) {
                return segments;
            }
        }
        return extractSegmentsFromOutput(output);
    }

    @SuppressWarnings("unchecked")
    List<TimedTextSegment> extractSegmentsFromTranscription(Map<String, Object> transcription) {
        if (transcription == null) {
            return List.of();
        }
        List<TimedTextSegment> segments = new ArrayList<>();
        List<Map<String, Object>> transcripts = (List<Map<String, Object>>) transcription.getOrDefault("transcripts", List.of());
        for (Map<String, Object> transcript : transcripts) {
            List<Map<String, Object>> sentences = (List<Map<String, Object>>) transcript.getOrDefault("sentences", List.of());
            for (Map<String, Object> sentence : sentences) {
                TimedTextSegment segment = toTimedSegment(sentence);
                if (segment != null) {
                    segments.add(segment);
                }
            }
        }
        return segments.stream()
                .sorted(Comparator.comparingLong(TimedTextSegment::startTimeMs))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<TimedTextSegment> extractSegmentsFromOutput(Map<String, Object> output) {
        Object text = output.get("text");
        if (text != null) {
            return List.of(new TimedTextSegment(0, 0, text.toString(), null));
        }
        List<Map<String, Object>> results = (List<Map<String, Object>>) output.getOrDefault("results", List.of());
        List<TimedTextSegment> segments = new ArrayList<>();
        for (Map<String, Object> result : results) {
            TimedTextSegment segment = toTimedSegment(result);
            if (segment != null) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private TimedTextSegment toTimedSegment(Map<String, Object> value) {
        String text = stringValue(value, "text", "transcript");
        if (text == null || text.isBlank()) {
            return null;
        }
        long begin = longValue(value, "begin_time", "start_time", "startTimeMs");
        long end = longValue(value, "end_time", "endTime", "endTimeMs");
        Integer speakerId = integerValue(value, "speaker_id", "speakerId");
        return new TimedTextSegment(begin, end, text, speakerId);
    }

    private long longValue(Map<String, Object> map, String... keys) {
        String value = stringValue(map, keys);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return (long) Double.parseDouble(value);
    }

    private Integer integerValue(Map<String, Object> map, String... keys) {
        String value = stringValue(map, keys);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private String stringValue(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    private String requiredApiKey() {
        String apiKey = properties.getDashscope().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 ai-search.models.dashscope.api-key");
        }
        return apiKey;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DashScope ASR 轮询被中断", ex);
        }
    }
}
