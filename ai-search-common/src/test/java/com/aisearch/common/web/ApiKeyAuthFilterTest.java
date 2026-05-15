package com.aisearch.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiKeyAuthFilterTest {
    @Test
    void allowsNormalApiRequestWithValidApiKey() throws ServletException, IOException {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("secret", "ADMIN");
        MockHttpServletRequest request = request("POST", "/api/search");
        request.addHeader("X-AI-Search-Api-Key", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsAdminDebugPathWithoutAdminRole() throws ServletException, IOException {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("secret", "ADMIN");
        MockHttpServletRequest request = request(
                "POST",
                "/api/workflows/video-indexing/videos/video-1/rebuild-index");
        request.addHeader("X-AI-Search-Api-Key", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void allowsAdminDebugPathWithAdminRole() throws ServletException, IOException {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("secret", "ADMIN");
        MockHttpServletRequest request = request(
                "POST",
                "/api/workflows/video-indexing/videos/video-1/stages/OCR_PROCESSING/rerun");
        request.addHeader("X-AI-Search-Api-Key", "secret");
        request.addHeader("X-AI-Search-Roles", "USER, ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }
}
