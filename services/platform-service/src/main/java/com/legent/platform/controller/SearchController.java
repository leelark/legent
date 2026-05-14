package com.legent.platform.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.platform.domain.SearchIndexDoc;
import com.legent.platform.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


import org.springframework.security.access.prepost.PreAuthorize;
import com.legent.security.TenantContext;

@RestController
@RequestMapping({"/api/v1/platform/search", "/api/v1/admin/search"})
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'PLATFORM_ADMIN')")
public class SearchController {

    private final GlobalSearchService searchService;

    @GetMapping
    public ApiResponse<List<SearchIndexDoc>> search(@RequestParam String q) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(searchService.search(tenantId, q));
    }
}
