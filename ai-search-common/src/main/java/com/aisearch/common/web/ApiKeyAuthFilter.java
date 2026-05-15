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
    private static final String ROLES_HEADER = "X-AI-Search-Roles";

    private final String apiKey;
    private final String adminRole;

    public ApiKeyAuthFilter(
            @Value("${ai-search.security.api-key:}") String apiKey,
            @Value("${ai-search.security.admin-role:ADMIN}") String adminRole) {
        this.apiKey = apiKey;
        this.adminRole = adminRole;
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
        if (isAdminOnlyPath(request) && !hasAdminRole(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Forbidden\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAdminOnlyPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/workflows/video-indexing/videos/")) {
            return false;
        }
        return uri.endsWith("/slice-plan")
                || uri.endsWith("/artifacts")
                || uri.endsWith("/segments/artifacts")
                || uri.endsWith("/rebuild-index")
                || uri.endsWith("/delete-index")
                || uri.contains("/stages/") && uri.endsWith("/rerun");
    }

    private boolean hasAdminRole(HttpServletRequest request) {
        String roles = request.getHeader(ROLES_HEADER);
        if (!StringUtils.hasText(roles) || !StringUtils.hasText(adminRole)) {
            return false;
        }
        for (String role : roles.split(",")) {
            if (adminRole.equals(role.trim())) {
                return true;
            }
        }
        return false;
    }
}
