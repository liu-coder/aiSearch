package com.aisearch.worker.infrastructure.model;

import com.aisearch.common.asr.TimedTextSegment;
import com.aisearch.worker.infrastructure.config.WorkerPipelineProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Worker 调用 model-service 的客户端，统一封装 ASR、OCR、Caption 和 Embedding 入口。
 */
@Component
public class ModelGatewayClient {
    private final RestClient restClient;
    private final Semaphore modelCallLimiter;
    private final int cacheMaxEntries;
    private final Map<String, Object> cache;

    public ModelGatewayClient(RestClient.Builder builder, WorkerPipelineProperties properties) {
        this.restClient = builder.baseUrl(properties.getModel().getEndpoint()).build();
        this.modelCallLimiter = new Semaphore(Math.max(1, properties.getModel().getMaxConcurrentCalls()));
        this.cacheMaxEntries = Math.max(0, properties.getModel().getCacheMaxEntries());
        this.cache = new LinkedHashMap<>(16, 0.75f, true);
    }

    /**
     * 调用语音识别，将音频 URL 转换为可索引文本。
     */
    public String asr(String fileUrl) {
        return stringData("/api/models/asr", Map.of("fileUrl", fileUrl));
    }

    /**
     * 调用带时间戳 ASR，将句子级转写结果返回给片段对齐逻辑。
     */
    public List<TimedTextSegment> asrSegments(String fileUrl) {
        Object data = cachedResponseData("/api/models/asr-segments", Map.of("fileUrl", fileUrl));
        if (data instanceof List<?> values) {
            List<TimedTextSegment> segments = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof Map<?, ?> map) {
                    segments.add(new TimedTextSegment(
                            longValue(map.get("startTimeMs")),
                            longValue(map.get("endTimeMs")),
                            stringValue(map.get("text")),
                            integerValue(map.get("speakerId"))));
                }
            }
            return segments;
        }
        throw new IllegalStateException("模型服务 ASR 分段返回格式不正确: " + data);
    }

    /**
     * 调用视觉 OCR，提取关键帧中的可见文字。
     */
    public String ocr(String imageUrl) {
        return stringData("/api/models/ocr", Map.of("imageUrl", imageUrl));
    }

    /**
     * 调用视觉描述模型，为关键帧生成场景语义描述。
     */
    public String caption(String imageUrl) {
        return stringData("/api/models/caption", Map.of("imageUrl", imageUrl));
    }

    /**
     * 调用 embedding 接口，返回 Milvus 写入需要的向量。
     */
    public List<Double> embedding(String text) {
        Object data = cachedResponseData("/api/models/embeddings", Map.of("input", text));
        if (data instanceof List<?> values) {
            return values.stream()
                    .map(value -> ((Number) value).doubleValue())
                    .toList();
        }
        throw new IllegalStateException("模型服务 embedding 返回格式不正确: " + data);
    }

    /**
     * 调用图片 embedding 接口，返回关键帧图片向量。
     */
    public List<Double> imageEmbedding(String imageUrl) {
        Object data = cachedResponseData("/api/models/image-embedding", Map.of("imageUrl", imageUrl));
        if (data instanceof List<?> values) {
            return values.stream()
                    .map(value -> ((Number) value).doubleValue())
                    .toList();
        }
        throw new IllegalStateException("模型服务 image embedding 返回格式不正确: " + data);
    }

    private String stringData(String path, Object body) {
        Object data = cachedResponseData(path, body);
        return data == null ? "" : data.toString();
    }

    private Object cachedResponseData(String path, Object body) {
        String key = path + "|" + body;
        if (cacheMaxEntries > 0) {
            synchronized (cache) {
                if (cache.containsKey(key)) {
                    return cache.get(key);
                }
            }
        }
        Object data = limitedResponseData(path, body);
        if (cacheMaxEntries > 0) {
            synchronized (cache) {
                cache.put(key, data);
                while (cache.size() > cacheMaxEntries) {
                    String eldestKey = cache.keySet().iterator().next();
                    cache.remove(eldestKey);
                }
            }
        }
        return data;
    }

    private Object limitedResponseData(String path, Object body) {
        boolean acquired = false;
        try {
            modelCallLimiter.acquire();
            acquired = true;
            return responseData(path, body);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("模型调用等待并发令牌时被中断", ex);
        } finally {
            if (acquired) {
                modelCallLimiter.release();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object responseData(String path, Object body) {
        /*
         * 模型调用步骤：
         * 1. POST 到 model-service 对应能力接口。
         * 2. 校验统一 ApiResponse.success，失败时抛出异常进入 worker 重试。
         * 3. 只把 data 暴露给阶段处理器，避免业务层耦合 HTTP 响应结构。
         */
        Map<String, Object> response = restClient.post()
                .uri(path)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            Object message = response == null ? "空响应" : response.get("message");
            throw new IllegalStateException("模型服务调用失败: " + message);
        }
        return response.get("data");
    }

    private long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return (long) Double.parseDouble(value.toString());
    }

    private Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
