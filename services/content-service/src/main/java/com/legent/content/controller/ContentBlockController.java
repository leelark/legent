package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.content.domain.ContentBlock;
import com.legent.content.domain.ContentBlockVersion;
import com.legent.content.dto.ContentBlockDto;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.service.ContentBlockService;
import com.legent.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/content/blocks")
@RequiredArgsConstructor
public class ContentBlockController {

    private final ContentBlockService blockService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('content:write', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<ContentBlockDto.Response> createBlock(@Valid @RequestBody ContentBlockDto.Create request) {
        ContentBlock block = blockService.createBlock(request);
        return ApiResponse.ok(mapToResponse(block));
    }

    @GetMapping("/{id}")
    public ApiResponse<ContentBlockDto.Response> getBlock(@PathVariable @org.springframework.lang.NonNull String id) {

        final String tenantId = TenantContext.requireTenantId();

        ContentBlock block = blockService.getBlock(tenantId, id);
        return ApiResponse.ok(mapToResponse(block));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('content:write', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<ContentBlockDto.Response> updateBlock(@PathVariable @org.springframework.lang.NonNull String id,
            @Valid @RequestBody ContentBlockDto.Create request) {
        final String tenantId = TenantContext.requireTenantId();
        ContentBlock updatedBlock = blockService.updateBlock(tenantId, id, request);
        return ApiResponse.ok(mapToResponse(updatedBlock));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacEvaluator.hasPermission('content:delete', principal.roles) or @rbacEvaluator.hasPermission('content:*', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public void deleteBlock(@PathVariable @org.springframework.lang.NonNull String id) {
        final String tenantId = TenantContext.requireTenantId();
        blockService.deleteBlock(tenantId, id);
    }

    @GetMapping
    public PagedResponse<ContentBlockDto.Response> listBlocks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.requireTenantId();
        Page<ContentBlock> blocks = blockService.listBlocks(tenantId,
                PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE)));
        return PagedResponse.of(
                blocks.getContent().stream().map(this::mapToResponse).toList(),
                page,
                size,
                blocks.getTotalElements(),
                blocks.getTotalPages());
    }

    @GetMapping("/global")
    public ApiResponse<List<ContentBlockDto.Response>> listGlobalBlocks() {
        String tenantId = TenantContext.requireTenantId();
        List<ContentBlock> blocks = blockService.listGlobalBlocks(tenantId);
        return ApiResponse.ok(blocks.stream().map(this::mapToResponse).toList());
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('content:write', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<EmailStudioDto.ContentBlockVersionResponse> createVersion(
            @PathVariable String id,
            @Valid @RequestBody EmailStudioDto.ContentBlockVersionRequest request) {
        String tenantId = TenantContext.requireTenantId();
        ContentBlockVersion version = blockService.createVersion(tenantId, id, request);
        return ApiResponse.ok(mapVersion(version));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<EmailStudioDto.ContentBlockVersionResponse>> listVersions(@PathVariable String id) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(blockService.listVersions(tenantId, id).stream().map(this::mapVersion).toList());
    }

    @PostMapping("/{id}/versions/{versionNumber}/publish")
    @PreAuthorize("@rbacEvaluator.hasPermission('content:publish', principal.roles) or @rbacEvaluator.hasPermission('content:*', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<EmailStudioDto.ContentBlockVersionResponse> publishVersion(
            @PathVariable String id,
            @PathVariable Integer versionNumber) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapVersion(blockService.publishVersion(tenantId, id, versionNumber)));
    }

    @PostMapping("/{id}/rollback/{versionNumber}")
    @PreAuthorize("@rbacEvaluator.hasPermission('content:write', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<EmailStudioDto.ContentBlockVersionResponse> rollbackVersion(
            @PathVariable String id,
            @PathVariable Integer versionNumber) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapVersion(blockService.rollbackVersion(tenantId, id, versionNumber)));
    }

    private ContentBlockDto.Response mapToResponse(ContentBlock block) {
        ContentBlockDto.Response response = new ContentBlockDto.Response();
        response.setId(block.getId());
        response.setName(block.getName());
        response.setBlockType(block.getBlockType());
        response.setContent(block.getContent());
        response.setStyles(block.getStyles());
        response.setSettings(block.getSettings());
        response.setIsGlobal(block.getIsGlobal());
        return response;
    }

    private EmailStudioDto.ContentBlockVersionResponse mapVersion(ContentBlockVersion version) {
        EmailStudioDto.ContentBlockVersionResponse response = new EmailStudioDto.ContentBlockVersionResponse();
        response.setId(version.getId());
        response.setBlockId(version.getBlock() != null ? version.getBlock().getId() : null);
        response.setVersionNumber(version.getVersionNumber());
        response.setContent(version.getContent());
        response.setStyles(version.getStyles());
        response.setSettings(version.getSettings());
        response.setChanges(version.getChanges());
        response.setIsPublished(version.getIsPublished());
        response.setCreatedAt(version.getCreatedAt() != null ? version.getCreatedAt().toString() : null);
        response.setUpdatedAt(version.getUpdatedAt() != null ? version.getUpdatedAt().toString() : null);
        return response;
    }
}
