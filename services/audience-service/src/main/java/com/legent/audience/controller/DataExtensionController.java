package com.legent.audience.controller;

import com.legent.audience.dto.DataExtensionDto;
import com.legent.audience.service.DataExtensionQueryActivityService;
import com.legent.audience.service.DataExtensionService;
import com.legent.common.constant.AppConstants;
import com.legent.common.security.InternalApiTokenValidator;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/data-extensions")
@RequiredArgsConstructor
public class DataExtensionController {

    private final DataExtensionService deService;
    private final DataExtensionQueryActivityService queryActivityService;

    @Value("${legent.internal.api-token}")
    private String internalApiToken;

    @PostConstruct
    void validateInternalApiToken() {
        InternalApiTokenValidator.requireConfigured("legent.internal.api-token", internalApiToken);
    }

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public PagedResponse<DataExtensionDto.Response> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int boundedPage = boundedPage(page);
        int boundedSize = boundedSize(size);
        Page<DataExtensionDto.Response> result = deService.list(PageRequest.of(boundedPage, boundedSize));
        return PagedResponse.of(result.getContent(), boundedPage, boundedSize, result.getTotalElements(), result.getTotalPages());
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

    @PutMapping("/{id}/governance")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<DataExtensionDto.Response> updateGovernance(@PathVariable String id,
                                                                    @Valid @RequestBody DataExtensionDto.GovernanceMetadata request) {
        return ApiResponse.ok(deService.updateGovernance(id, request));
    }

    @GetMapping("/{id}/governance-audit")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<java.util.List<DataExtensionDto.GovernanceAuditResponse>> listGovernanceAudit(@PathVariable String id) {
        return ApiResponse.ok(deService.listGovernanceAudit(id));
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
        int boundedPage = boundedPage(page);
        int boundedSize = boundedSize(size);
        Page<DataExtensionDto.RecordResponse> result = deService.listRecords(deId, PageRequest.of(boundedPage, boundedSize));
        return PagedResponse.of(result.getContent(), boundedPage, boundedSize, result.getTotalElements(), result.getTotalPages());
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

    @PostMapping("/query-activities/internal")
    @PreAuthorize("permitAll()")
    public ApiResponse<DataExtensionDto.SqlQueryActivityResponse> runSqlQueryActivity(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody DataExtensionDto.SqlQueryActivityRequest request) {
        if (!InternalApiTokenValidator.matches(internalApiToken, token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
        }
        return ApiResponse.ok(queryActivityService.execute(request));
    }

    private int boundedPage(int page) {
        return Math.max(page, 0);
    }

    private int boundedSize(int size) {
        if (size < 1) {
            return AppConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, AppConstants.MAX_PAGE_SIZE);
    }
}
