package com.legent.audience.controller;

import com.legent.audience.dto.DataExtensionDto;
import com.legent.audience.service.DataExtensionService;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/data-extensions")
@RequiredArgsConstructor
public class DataExtensionController {

    private final DataExtensionService deService;

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public PagedResponse<DataExtensionDto.Response> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DataExtensionDto.Response> result = deService.list(PageRequest.of(page, size));
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<DataExtensionDto.Response> getById(@PathVariable String id) {
        return ApiResponse.ok(deService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<DataExtensionDto.Response> create(@Valid @RequestBody DataExtensionDto.CreateRequest request) {
        return ApiResponse.ok(deService.create(request));
    }

    @PutMapping("/{id}/schema")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<DataExtensionDto.Response> updateSchema(@PathVariable String id,
                                                                @Valid @RequestBody DataExtensionDto.SchemaUpdateRequest request) {
        return ApiResponse.ok(deService.updateSchema(id, request));
    }

    @PutMapping("/{id}/sendable")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<DataExtensionDto.Response> updateSendableConfig(@PathVariable String id,
                                                                        @Valid @RequestBody DataExtensionDto.SendableConfigRequest request) {
        return ApiResponse.ok(deService.updateSendableConfig(id, request));
    }

    @PutMapping("/{id}/retention")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<DataExtensionDto.Response> updateRetentionPolicy(@PathVariable String id,
                                                                         @Valid @RequestBody DataExtensionDto.RetentionPolicyRequest request) {
        return ApiResponse.ok(deService.updateRetentionPolicy(id, request));
    }

    @PutMapping("/{id}/relationships")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<DataExtensionDto.Response> updateRelationships(@PathVariable String id,
                                                                       @Valid @RequestBody DataExtensionDto.RelationshipRequest request) {
        return ApiResponse.ok(deService.updateRelationships(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:delete', principal.roles)")
    public void delete(@PathVariable String id) {
        deService.deleteDataExtension(id);
    }

    @PostMapping("/{deId}/records")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<DataExtensionDto.RecordResponse> addRecord(@PathVariable String deId,
                                                                   @Valid @RequestBody DataExtensionDto.RecordRequest request) {
        return ApiResponse.ok(deService.addRecord(deId, request));
    }

    @GetMapping("/{deId}/records")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public PagedResponse<DataExtensionDto.RecordResponse> listRecords(@PathVariable String deId,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        Page<DataExtensionDto.RecordResponse> result = deService.listRecords(deId, PageRequest.of(page, size));
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/{deId}/query-preview")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<DataExtensionDto.QueryPreviewResponse> previewQuery(
            @PathVariable String deId,
            @Valid @RequestBody(required = false) DataExtensionDto.QueryPreviewRequest request) {
        return ApiResponse.ok(deService.previewQuery(deId, request));
    }

    @PostMapping("/{deId}/import-mapping/preview")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<DataExtensionDto.ImportMappingPreviewResponse> previewImportMapping(
            @PathVariable String deId,
            @Valid @RequestBody DataExtensionDto.ImportMappingPreviewRequest request) {
        return ApiResponse.ok(deService.previewImportMapping(deId, request));
    }
}
