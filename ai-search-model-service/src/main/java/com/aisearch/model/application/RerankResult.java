package com.aisearch.model.application;

/**
 * 精排结果，index 对应原 documents 下标。
 */
public record RerankResult(int index, double score, String document) {
}
