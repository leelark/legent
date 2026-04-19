package com.legent.platform.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.platform.domain.SearchIndexDoc;
import com.legent.platform.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/platform/search")
@RequiredArgsConstructor
public class SearchController {

    private final GlobalSearchService searchService;

    @GetMapping
    public ApiResponse<List<SearchIndexDoc>> search(
            @RequestHeader("X-Tenant-Id") String tenantId, 
            @RequestParam String q) {
        return ApiResponse.ok(searchService.search(tenantId, q));
    }
}
