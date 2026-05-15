package com.aisearch.model.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型服务配置。provider=deterministic 用于本地；provider=http 用于对接外部模型网关。
 */
@ConfigurationProperties(prefix = "ai-search.models")
public class ModelProviderProperties {
    private String provider = "deterministic";
    private int embeddingDimension = 1024;
    private final Http http = new Http();
    private final DashScope dashscope = new DashScope();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public Http getHttp() {
        return http;
    }

    public DashScope getDashscope() {
        return dashscope;
    }

    public static class Http {
        private String endpoint = "http://localhost:19090/embedding";
        private String apiKey;
        private String model = "bge-m3";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class DashScope {
        private String apiKey;
        private String embeddingEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
        private String chatEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
        private String asrSubmitEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcription";
        private String taskEndpoint = "https://dashscope.aliyuncs.com/api/v1/tasks/{taskId}";
        private String rerankEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
        private int embeddingDimension = 1024;
        private String textEmbeddingModel = "text-embedding-v4";
        private String multimodalEmbeddingModel = "multimodal-embedding-v1";
        private String asrModel = "paraformer-v2";
        private String ocrModel = "qwen-vl-ocr";
        private String captionModel = "qwen3-vl-flash";
        private String analysisModel = "qwen-plus";
        private String rerankModel = "gte-rerank-v2";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getEmbeddingEndpoint() {
            return embeddingEndpoint;
        }

        public void setEmbeddingEndpoint(String embeddingEndpoint) {
            this.embeddingEndpoint = embeddingEndpoint;
        }

        public String getChatEndpoint() {
            return chatEndpoint;
        }

        public void setChatEndpoint(String chatEndpoint) {
            this.chatEndpoint = chatEndpoint;
        }

        public String getAsrSubmitEndpoint() {
            return asrSubmitEndpoint;
        }

        public void setAsrSubmitEndpoint(String asrSubmitEndpoint) {
            this.asrSubmitEndpoint = asrSubmitEndpoint;
        }

        public String getTaskEndpoint() {
            return taskEndpoint;
        }

        public void setTaskEndpoint(String taskEndpoint) {
            this.taskEndpoint = taskEndpoint;
        }

        public String getRerankEndpoint() {
            return rerankEndpoint;
        }

        public void setRerankEndpoint(String rerankEndpoint) {
            this.rerankEndpoint = rerankEndpoint;
        }

        public String getTextEmbeddingModel() {
            return textEmbeddingModel;
        }

        public int getEmbeddingDimension() {
            return embeddingDimension;
        }

        public void setEmbeddingDimension(int embeddingDimension) {
            this.embeddingDimension = embeddingDimension;
        }

        public void setTextEmbeddingModel(String textEmbeddingModel) {
            this.textEmbeddingModel = textEmbeddingModel;
        }

        public String getMultimodalEmbeddingModel() {
            return multimodalEmbeddingModel;
        }

        public void setMultimodalEmbeddingModel(String multimodalEmbeddingModel) {
            this.multimodalEmbeddingModel = multimodalEmbeddingModel;
        }

        public String getAsrModel() {
            return asrModel;
        }

        public void setAsrModel(String asrModel) {
            this.asrModel = asrModel;
        }

        public String getOcrModel() {
            return ocrModel;
        }

        public void setOcrModel(String ocrModel) {
            this.ocrModel = ocrModel;
        }

        public String getCaptionModel() {
            return captionModel;
        }

        public void setCaptionModel(String captionModel) {
            this.captionModel = captionModel;
        }

        public String getAnalysisModel() {
            return analysisModel;
        }

        public void setAnalysisModel(String analysisModel) {
            this.analysisModel = analysisModel;
        }

        public String getRerankModel() {
            return rerankModel;
        }

        public void setRerankModel(String rerankModel) {
            this.rerankModel = rerankModel;
        }
    }

}
