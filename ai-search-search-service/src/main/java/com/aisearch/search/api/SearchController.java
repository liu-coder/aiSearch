package com.aisearch.search.api;

import com.aisearch.common.api.ApiResponse;
import com.aisearch.common.search.SearchRequest;
import com.aisearch.common.search.SearchResponse;
import com.aisearch.search.application.SearchUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索 HTTP 接口层，只负责参数校验和委托用例，业务流程放在 application 层。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchUseCase searchUseCase;

    public SearchController(SearchUseCase searchUseCase) {
        this.searchUseCase = searchUseCase;
    }

    @PostMapping
    public ApiResponse<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        return ApiResponse.ok(searchUseCase.search(request));
    }
}
