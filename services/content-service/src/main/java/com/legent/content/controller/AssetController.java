package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.content.domain.Asset;
import com.legent.content.service.AssetService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('content:read', principal.roles)")
    public PagedResponse<Asset> listAssets(
            Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String contentType) {
        String tenantId = TenantContext.requireTenantId();
        Page<Asset> page;
        if ((q != null && !q.isBlank()) || (contentType != null && !contentType.isBlank())) {
            page = assetService.searchAssets(tenantId, q, contentType, pageable);
        } else {
            page = assetService.listAssets(tenantId, pageable);
        }
        return PagedResponse.from(page);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@rbacEvaluator.hasPermission('content:write', principal.roles)")
    public ApiResponse<Asset> uploadAsset(@RequestParam("file") MultipartFile file) {
        TenantContext.requireTenantId();
        return ApiResponse.ok(assetService.uploadAsset(file, Map.of("source", "single-upload")));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@rbacEvaluator.hasPermission('content:write', principal.roles)")
    public ApiResponse<List<Asset>> bulkUpload(@RequestParam("files") MultipartFile[] files) {
        List<Asset> uploaded = assetService.uploadAssets(files == null ? List.of() : List.of(files));
        return ApiResponse.ok(uploaded);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('content:delete', principal.roles)")
    public void deleteAsset(@PathVariable String id) {
        String tenantId = TenantContext.requireTenantId();
        assetService.deleteAsset(java.util.Objects.requireNonNull(tenantId), java.util.Objects.requireNonNull(id));
    }
}
