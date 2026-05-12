package com.aisearch.worker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Worker 离线处理配置，集中管理工作目录、ES、Milvus 和模型维度。
 */
@ConfigurationProperties(prefix = "ai-search.worker")
public class WorkerPipelineProperties {
    private String workspace = "E:/workspace/docker/data/ai-search-worker";
    private int embeddingDimension = 1024;
    private boolean initializeIndexOnStartup = true;
    private int maxAttempts = 3;
    private long runningTimeoutMs = 300000;
    private final Elasticsearch elasticsearch = new Elasticsearch();
    private final Milvus milvus = new Milvus();
    private final Model model = new Model();
    private final Storage storage = new Storage();
    private final MediaStrategy mediaStrategy = new MediaStrategy();

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public boolean isInitializeIndexOnStartup() {
        return initializeIndexOnStartup;
    }

    public void setInitializeIndexOnStartup(boolean initializeIndexOnStartup) {
        this.initializeIndexOnStartup = initializeIndexOnStartup;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRunningTimeoutMs() {
        return runningTimeoutMs;
    }

    public void setRunningTimeoutMs(long runningTimeoutMs) {
        this.runningTimeoutMs = runningTimeoutMs;
    }

    public Elasticsearch getElasticsearch() {
        return elasticsearch;
    }

    public Milvus getMilvus() {
        return milvus;
    }

    public Model getModel() {
        return model;
    }

    public Storage getStorage() {
        return storage;
    }

    public MediaStrategy getMediaStrategy() {
        return mediaStrategy;
    }

    public static class Model {
        private String endpoint = "http://localhost:18084";
        private int maxConcurrentCalls = 4;
        private int qpsLimit = 8;
        private int maxAttempts = 3;
        private long initialBackoffMs = 200;
        private int cacheMaxEntries = 1000;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public int getMaxConcurrentCalls() {
            return maxConcurrentCalls;
        }

        public void setMaxConcurrentCalls(int maxConcurrentCalls) {
            this.maxConcurrentCalls = maxConcurrentCalls;
        }

        public int getQpsLimit() {
            return qpsLimit;
        }

        public void setQpsLimit(int qpsLimit) {
            this.qpsLimit = qpsLimit;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialBackoffMs() {
            return initialBackoffMs;
        }

        public void setInitialBackoffMs(long initialBackoffMs) {
            this.initialBackoffMs = initialBackoffMs;
        }

        public int getCacheMaxEntries() {
            return cacheMaxEntries;
        }

        public void setCacheMaxEntries(int cacheMaxEntries) {
            this.cacheMaxEntries = cacheMaxEntries;
        }
    }

    public static class Storage {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String processedBucket = "ai-video-processed";
        private int presignedExpireMinutes = 60;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getProcessedBucket() {
            return processedBucket;
        }

        public void setProcessedBucket(String processedBucket) {
            this.processedBucket = processedBucket;
        }

        public int getPresignedExpireMinutes() {
            return presignedExpireMinutes;
        }

        public void setPresignedExpireMinutes(int presignedExpireMinutes) {
            this.presignedExpireMinutes = presignedExpireMinutes;
        }
    }

    public static class MediaStrategy {
        private long defaultDurationSeconds = 60;
        private long shortVideoSeconds = 120;
        private long mediumVideoSeconds = 900;
        private long shortSegmentSeconds = 15;
        private long mediumSegmentSeconds = 45;
        private long longSegmentSeconds = 120;
        private int maxFrames = 12;
        private int maxSegments = 12;
        private long largeFileBytes = 524288000;
        private int largeFileMaxFrames = 8;
        private boolean contentAwareEnabled = true;
        private boolean sceneDetectionEnabled = true;
        private boolean silenceDetectionEnabled = true;
        private double sceneThreshold = 0.35;
        private String silenceNoise = "-30dB";
        private double silenceDurationSeconds = 0.8;
        private long minContentSegmentSeconds = 8;
        private long maxContentSegmentSeconds = 90;

        public long getDefaultDurationSeconds() {
            return defaultDurationSeconds;
        }

        public void setDefaultDurationSeconds(long defaultDurationSeconds) {
            this.defaultDurationSeconds = defaultDurationSeconds;
        }

        public long getShortVideoSeconds() {
            return shortVideoSeconds;
        }

        public void setShortVideoSeconds(long shortVideoSeconds) {
            this.shortVideoSeconds = shortVideoSeconds;
        }

        public long getMediumVideoSeconds() {
            return mediumVideoSeconds;
        }

        public void setMediumVideoSeconds(long mediumVideoSeconds) {
            this.mediumVideoSeconds = mediumVideoSeconds;
        }

        public long getShortSegmentSeconds() {
            return shortSegmentSeconds;
        }

        public void setShortSegmentSeconds(long shortSegmentSeconds) {
            this.shortSegmentSeconds = shortSegmentSeconds;
        }

        public long getMediumSegmentSeconds() {
            return mediumSegmentSeconds;
        }

        public void setMediumSegmentSeconds(long mediumSegmentSeconds) {
            this.mediumSegmentSeconds = mediumSegmentSeconds;
        }

        public long getLongSegmentSeconds() {
            return longSegmentSeconds;
        }

        public void setLongSegmentSeconds(long longSegmentSeconds) {
            this.longSegmentSeconds = longSegmentSeconds;
        }

        public int getMaxFrames() {
            return maxFrames;
        }

        public void setMaxFrames(int maxFrames) {
            this.maxFrames = maxFrames;
        }

        public int getMaxSegments() {
            return maxSegments;
        }

        public void setMaxSegments(int maxSegments) {
            this.maxSegments = maxSegments;
        }

        public long getLargeFileBytes() {
            return largeFileBytes;
        }

        public void setLargeFileBytes(long largeFileBytes) {
            this.largeFileBytes = largeFileBytes;
        }

        public int getLargeFileMaxFrames() {
            return largeFileMaxFrames;
        }

        public void setLargeFileMaxFrames(int largeFileMaxFrames) {
            this.largeFileMaxFrames = largeFileMaxFrames;
        }

        public boolean isContentAwareEnabled() {
            return contentAwareEnabled;
        }

        public void setContentAwareEnabled(boolean contentAwareEnabled) {
            this.contentAwareEnabled = contentAwareEnabled;
        }

        public boolean isSceneDetectionEnabled() {
            return sceneDetectionEnabled;
        }

        public void setSceneDetectionEnabled(boolean sceneDetectionEnabled) {
            this.sceneDetectionEnabled = sceneDetectionEnabled;
        }

        public boolean isSilenceDetectionEnabled() {
            return silenceDetectionEnabled;
        }

        public void setSilenceDetectionEnabled(boolean silenceDetectionEnabled) {
            this.silenceDetectionEnabled = silenceDetectionEnabled;
        }

        public double getSceneThreshold() {
            return sceneThreshold;
        }

        public void setSceneThreshold(double sceneThreshold) {
            this.sceneThreshold = sceneThreshold;
        }

        public String getSilenceNoise() {
            return silenceNoise;
        }

        public void setSilenceNoise(String silenceNoise) {
            this.silenceNoise = silenceNoise;
        }

        public double getSilenceDurationSeconds() {
            return silenceDurationSeconds;
        }

        public void setSilenceDurationSeconds(double silenceDurationSeconds) {
            this.silenceDurationSeconds = silenceDurationSeconds;
        }

        public long getMinContentSegmentSeconds() {
            return minContentSegmentSeconds;
        }

        public void setMinContentSegmentSeconds(long minContentSegmentSeconds) {
            this.minContentSegmentSeconds = minContentSegmentSeconds;
        }

        public long getMaxContentSegmentSeconds() {
            return maxContentSegmentSeconds;
        }

        public void setMaxContentSegmentSeconds(long maxContentSegmentSeconds) {
            this.maxContentSegmentSeconds = maxContentSegmentSeconds;
        }
    }

    public static class Elasticsearch {
        private String endpoint = "http://localhost:9200";
        private String index = "ai_video_segments";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getIndex() {
            return index;
        }

        public void setIndex(String index) {
            this.index = index;
        }
    }

    public static class Milvus {
        private String endpoint = "http://localhost:19530";
        private String collectionName = "ai_video_segments";
        private String primaryField = "segmentId";
        private String vectorField = "embedding";
        private String imageVectorField = "imageEmbedding";
        private String metricType = "COSINE";
        private String token;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getCollectionName() {
            return collectionName;
        }

        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }

        public String getPrimaryField() {
            return primaryField;
        }

        public void setPrimaryField(String primaryField) {
            this.primaryField = primaryField;
        }

        public String getVectorField() {
            return vectorField;
        }

        public void setVectorField(String vectorField) {
            this.vectorField = vectorField;
        }

        public String getImageVectorField() {
            return imageVectorField;
        }

        public void setImageVectorField(String imageVectorField) {
            this.imageVectorField = imageVectorField;
        }

        public String getMetricType() {
            return metricType;
        }

        public void setMetricType(String metricType) {
            this.metricType = metricType;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
