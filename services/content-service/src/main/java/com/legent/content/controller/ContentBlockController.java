package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.content.domain.ContentBlock;
import com.legent.content.dto.ContentBlockDto;
import com.legent.content.service.ContentBlockService;
import com.legent.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/content/blocks")
@RequiredArgsConstructor
public class ContentBlockController {

    private final ContentBlockService blockService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
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
    public ApiResponse<ContentBlockDto.Response> updateBlock(@PathVariable @org.springframework.lang.NonNull String id,
            @Valid @RequestBody ContentBlockDto.Create request) {
        final String tenantId = TenantContext.requireTenantId();
        ContentBlock updatedBlock = blockService.updateBlock(tenantId, id, request);
        return ApiResponse.ok(mapToResponse(updatedBlock));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
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
}
