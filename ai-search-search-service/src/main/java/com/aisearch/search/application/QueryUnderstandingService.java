package com.aisearch.search.application;

import com.aisearch.common.search.QueryType;
import com.aisearch.common.search.RecallSource;
import com.aisearch.common.search.SearchRequest;
import com.aisearch.search.domain.QueryIntent;
import com.aisearch.search.domain.SearchIntent;
import com.aisearch.search.infrastructure.ModelEmbeddingClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 查询理解服务，负责判断输入类型并提取后续召回需要的轻量关键词。
 */
@Service
public class QueryUnderstandingService {
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*(?:到|至|-|~)\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})年?");
    private final ModelEmbeddingClient modelClient;

    QueryUnderstandingService() {
        this.modelClient = null;
    }

    public QueryUnderstandingService(ModelEmbeddingClient modelClient) {
        this.modelClient = modelClient;
    }

    public QueryIntent understand(SearchRequest request) {
        /*
         * 查询理解流程：
         * 1. 校验文字/图片至少存在一种，避免后续召回计划为空。
         * 2. 根据输入组合确定 QueryType，TEXT/IMAGE/MIXED 会走不同召回路径。
         * 3. 对文本做轻量 token 和过滤条件抽取，当前保留为可替换的规则实现。
         * 4. 推断业务意图并生成 recallSources，后续 SearchUseCase 只按计划编排。
         * 5. 归一化 semanticQuery，去掉 tag/author/type 这类结构化过滤语法。
         */
        boolean hasText = StringUtils.hasText(request.text());
        boolean hasImage = StringUtils.hasText(request.imageUrl());
        if (!hasText && !hasImage) {
            throw new IllegalArgumentException("text 或 imageUrl 至少需要提供一个");
        }
        QueryType type = hasText && hasImage ? QueryType.MIXED : hasImage ? QueryType.IMAGE : QueryType.TEXT;
        List<String> keywords = hasText ? tokenize(request.text()) : List.of();
        SearchIntent intent = inferIntent(type, keywords);
        Map<String, String> filters = extractFilters(keywords);
        List<RecallSource> recallSources = buildRecallPlan(type, intent, filters);
        String semanticQuery = hasText ? normalizeSemanticQuery(request.text(), filters) : "image:" + request.imageUrl();
        ModelUnderstanding modelUnderstanding = modelUnderstanding(request.text(), semanticQuery, filters);
        return new QueryIntent(
                type,
                modelUnderstanding.intent() == null ? intent : modelUnderstanding.intent(),
                request.text(),
                request.imageUrl(),
                modelUnderstanding.semanticQuery(),
                keywords,
                modelUnderstanding.filters(),
                recallSources,
                sourceWeights(type, recallSources));
    }

    private List<String> tokenize(String text) {
        // 当前先使用轻量空格切分；接入中文分词、实体识别和查询改写时替换这里。
        String normalized = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split(" ")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            tokens.add(normalized);
        }
        return tokens;
    }

    private SearchIntent inferIntent(QueryType type, List<String> keywords) {
        if (type == QueryType.IMAGE) {
            return SearchIntent.SIMILAR_IMAGE_SEARCH;
        }
        if (type == QueryType.MIXED) {
            return SearchIntent.MIXED_EVIDENCE_SEARCH;
        }
        boolean looksExactEntity = keywords.stream().anyMatch(this::looksLikeEntity);
        return looksExactEntity ? SearchIntent.EXACT_ENTITY_SEARCH : SearchIntent.SEMANTIC_VIDEO_SEARCH;
    }

    private boolean looksLikeEntity(String token) {
        return token.matches("[A-Za-z0-9_-]{3,}") || token.contains("发布会") || token.contains("型号");
    }

    private Map<String, String> extractFilters(List<String> keywords) {
        Map<String, String> filters = new LinkedHashMap<>();
        for (String keyword : keywords) {
            if (keyword.startsWith("tag:")) {
                filters.put("tag", keyword.substring("tag:".length()));
            } else if (keyword.startsWith("author:")) {
                filters.put("author", keyword.substring("author:".length()));
            } else if (keyword.startsWith("type:")) {
                filters.put("contentType", keyword.substring("type:".length()));
            }
        }
        String text = String.join(" ", keywords);
        Matcher matcher = DATE_RANGE_PATTERN.matcher(text);
        if (matcher.find()) {
            filters.put("startDate", matcher.group(1));
            filters.put("endDate", matcher.group(2));
        } else {
            Matcher year = YEAR_PATTERN.matcher(text);
            if (year.find()) {
                filters.put("startDate", year.group(1) + "-01-01");
                filters.put("endDate", year.group(1) + "-12-31");
            }
        }
        return filters;
    }

    private List<RecallSource> buildRecallPlan(QueryType type, SearchIntent intent, Map<String, String> filters) {
        List<RecallSource> sources = new ArrayList<>();
        if (type == QueryType.TEXT || type == QueryType.MIXED) {
            sources.add(RecallSource.KEYWORD);
            sources.add(RecallSource.TEXT_VECTOR);
            sources.add(RecallSource.ASR);
            sources.add(RecallSource.OCR);
        }
        if (type == QueryType.IMAGE || type == QueryType.MIXED) {
            sources.add(RecallSource.IMAGE_VECTOR);
            sources.add(RecallSource.SEGMENT_VECTOR);
        }
        if (intent == SearchIntent.EXACT_ENTITY_SEARCH || !filters.isEmpty()) {
            sources.add(RecallSource.METADATA);
        }
        return sources.stream().distinct().toList();
    }

    private String normalizeSemanticQuery(String text, Map<String, String> filters) {
        String semantic = text == null ? "" : text.trim();
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            semantic = semantic.replace(filter.getKey() + ":" + filter.getValue(), "").trim();
        }
        semantic = DATE_RANGE_PATTERN.matcher(semantic).replaceAll(" ");
        semantic = semantic.replaceAll("(^|\\s)(找|搜索|查询|检索|帮我找)(\\s|$)", " ");
        if (filters.containsKey("tag")) {
            semantic = semantic + " " + filters.get("tag");
        }
        if (filters.containsKey("author")) {
            semantic = semantic + " " + filters.get("author");
        }
        semantic = semantic.trim().replaceAll("\\s+", " ");
        return semantic.isEmpty() ? text : semantic;
    }

    private Map<RecallSource, Double> sourceWeights(QueryType type, List<RecallSource> sources) {
        Map<RecallSource, Double> weights = new LinkedHashMap<>();
        for (RecallSource source : sources) {
            double weight = switch (source) {
                case IMAGE_VECTOR -> type == QueryType.IMAGE ? 1.25 : 1.15;
                case TEXT_VECTOR, SEGMENT_VECTOR -> 1.1;
                case KEYWORD, ASR, OCR -> 1.0;
                case METADATA -> 0.9;
            };
            weights.put(source, weight);
        }
        return weights;
    }

    private ModelUnderstanding modelUnderstanding(String rawText, String semanticQuery, Map<String, String> ruleFilters) {
        if (modelClient == null || !StringUtils.hasText(rawText)) {
            return new ModelUnderstanding(null, semanticQuery, ruleFilters);
        }
        try {
            String response = modelClient.analyze("QUERY_UNDERSTANDING\n" + rawText, List.of(
                    "请输出 rewrite=查询改写",
                    "可选输出 intent=SEMANTIC_VIDEO_SEARCH|EXACT_ENTITY_SEARCH|MIXED_EVIDENCE_SEARCH",
                    "可选输出 person=人物 scene=场景 startDate=YYYY-MM-DD endDate=YYYY-MM-DD"));
            return parseModelUnderstanding(response, semanticQuery, ruleFilters);
        } catch (RuntimeException ex) {
            return new ModelUnderstanding(null, semanticQuery, ruleFilters);
        }
    }

    private ModelUnderstanding parseModelUnderstanding(String response, String fallbackSemanticQuery, Map<String, String> ruleFilters) {
        Map<String, String> filters = new LinkedHashMap<>(ruleFilters);
        String rewritten = fallbackSemanticQuery;
        SearchIntent modelIntent = null;
        if (response != null) {
            for (String line : response.split("\\R")) {
                int split = line.indexOf('=');
                if (split <= 0) {
                    continue;
                }
                String key = line.substring(0, split).trim();
                String value = line.substring(split + 1).trim();
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                if ("rewrite".equalsIgnoreCase(key)) {
                    rewritten = value;
                } else if ("intent".equalsIgnoreCase(key)) {
                    try {
                        modelIntent = SearchIntent.valueOf(value);
                    } catch (IllegalArgumentException ignored) {
                        modelIntent = null;
                    }
                } else if (List.of("person", "scene", "startDate", "endDate").contains(key)) {
                    filters.put(key, value);
                }
            }
        }
        return new ModelUnderstanding(modelIntent, rewritten, filters);
    }

    private record ModelUnderstanding(SearchIntent intent, String semanticQuery, Map<String, String> filters) {
    }
}
