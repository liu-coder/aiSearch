package com.aisearch.search.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 搜索后端配置，可通过 application.yml 或 Nacos 覆盖连接地址、索引名和字段名。
 */
@ConfigurationProperties(prefix = "ai-search.search")
public class SearchBackendProperties {
    private final Elasticsearch elasticsearch = new Elasticsearch();
    private final Milvus milvus = new Milvus();
    private final Model model = new Model();

    public Elasticsearch getElasticsearch() {
        return elasticsearch;
    }

    public Milvus getMilvus() {
        return milvus;
    }

    public Model getModel() {
        return model;
    }

    public static class Elasticsearch {
        private boolean enabled = true;
        private String endpoint = "http://localhost:9200";
        private String index = "ai_video_segments";
        private List<String> textFields = List.of("title^3", "asrText^2", "ocrText^2", "caption", "tags");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

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

        public List<String> getTextFields() {
            return textFields;
        }

        public void setTextFields(List<String> textFields) {
            this.textFields = textFields;
        }
    }

    public static class Milvus {
        private boolean enabled = true;
        private String endpoint = "http://localhost:19530";
        private String collectionName = "ai_video_segments";
        private String vectorField = "embedding";
        private String imageVectorField = "imageEmbedding";
        private String metricType = "COSINE";
        private List<String> outputFields = List.of("videoId", "segmentId", "title", "startTimeMs", "endTimeMs");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

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

        public List<String> getOutputFields() {
            return outputFields;
        }

        public void setOutputFields(List<String> outputFields) {
            this.outputFields = outputFields;
        }
    }

    public static class Model {
        private String endpoint = "http://localhost:18084";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }
}
