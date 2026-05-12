package com.aisearch.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 可选的内部 API Key 保护。未配置 key 时保持本地调试兼容；生产配置后保护上传、搜索和调试重跑接口。
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private static final String API_KEY_HEADER = "X-AI-Search-Api-Key";

    private final String apiKey;

    public ApiKeyAuthFilter(@Value("${ai-search.security.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !StringUtils.hasText(apiKey)
                || !request.getRequestURI().startsWith("/api/")
                || request.getRequestURI().startsWith("/api/models/embedding");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(API_KEY_HEADER);
        if (!apiKey.equals(provided)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
