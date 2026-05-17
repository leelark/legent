package com.legent.platform.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.platform.domain.SearchIndexDoc;
import com.legent.platform.service.GlobalSearchService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SearchController {

    private final GlobalSearchService searchService;

    @GetMapping("/api/v1/platform/search")
    @PreAuthorize("@rbacEvaluator.hasPermission('search:read', principal.roles) or @rbacEvaluator.hasPermission('workspace:read', principal.roles)")
    public ApiResponse<List<SearchIndexDoc>> search(@RequestParam String q) {
        return doSearch(q);
    }

    @GetMapping("/api/v1/admin/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<List<SearchIndexDoc>> adminSearch(@RequestParam String q) {
        return doSearch(q);
    }

    private ApiResponse<List<SearchIndexDoc>> doSearch(String q) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(searchService.search(tenantId, workspaceId, q));
    }
}
