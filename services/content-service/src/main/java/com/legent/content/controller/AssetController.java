package com.legent.content.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.content.domain.Asset;
import com.legent.content.dto.AssetDto;
import com.legent.content.service.AssetService;
import com.legent.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/content/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssetDto.Response> createAsset(@Valid @RequestBody AssetDto.Create request) {
        Asset asset = assetService.createAsset(request);
        return ApiResponse.ok(mapToResponse(asset));
    }

    @GetMapping("/{id}")
    public ApiResponse<AssetDto.Response> getAsset(@PathVariable String id) {
        String tenantId = TenantContext.requireTenantId();
        Asset asset = assetService.getAsset(tenantId, id);
        return ApiResponse.ok(mapToResponse(asset));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAsset(@PathVariable String id) {
        String tenantId = TenantContext.requireTenantId();
        assetService.deleteAsset(tenantId, id);
    }

    @GetMapping
    public PagedResponse<AssetDto.Response> listAssets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.requireTenantId();
        Page<Asset> assets = assetService.listAssets(tenantId, PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE)));
        return PagedResponse.of(
                assets.getContent().stream().map(this::mapToResponse).toList(),
                page,
                size,
                assets.getTotalElements(),
                assets.getTotalPages()
        );
    }

    private AssetDto.Response mapToResponse(Asset asset) {
        AssetDto.Response response = new AssetDto.Response();
        response.setId(asset.getId());
        response.setName(asset.getName());
        response.setUrl(asset.getStoragePath());
        response.setContentType(asset.getContentType());
        response.setSize(asset.getSizeBytes());
        response.setMetadata(parseMetadata(asset.getMetadata()));
        response.setCreatedAt(asset.getCreatedAt());
        response.setUpdatedAt(asset.getUpdatedAt());
        return response;
    }

    private Map<String, String> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(metadataJson, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}