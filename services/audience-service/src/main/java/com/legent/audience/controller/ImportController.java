package com.legent.audience.controller;

import com.legent.audience.dto.ImportDto;
import com.legent.audience.service.ImportService;
import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.common.security.InternalApiTokenValidator;
import com.legent.common.security.InternalServiceIdentity;
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
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class ImportController {

    private static final java.util.Set<String> ALLOWED_INTERNAL_IMPORT_SERVICES = java.util.Set.of("automation-service");

    private final ImportService importService;

    @Value("${legent.internal.api-token}")
    private String internalApiToken;

    @PostConstruct
    void validateInternalApiToken() {
        InternalApiTokenValidator.requireConfigured("legent.internal.api-token", internalApiToken);
    }

    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<ImportDto.StatusResponse> uploadImport(
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @RequestPart("request") @Valid ImportDto.StartRequest request) {
        return ApiResponse.ok(importService.uploadAndStartImport(file, request));
    }

    @PostMapping("/internal/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("permitAll()")
    public ApiResponse<ImportDto.StatusResponse> startInternalImport(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SERVICE, required = false) String internalService,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP, required = false) String signatureTimestamp,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE, required = false) String signature,
            @RequestHeader(name = AppConstants.HEADER_TENANT_ID) String tenantId,
            @RequestHeader(name = AppConstants.HEADER_WORKSPACE_ID) String workspaceId,
            @Valid @RequestBody ImportDto.StartRequest request) {
        if (!InternalServiceIdentity.matches(
                internalApiToken,
                token,
                internalService,
                ALLOWED_INTERNAL_IMPORT_SERVICES,
                tenantId,
                workspaceId,
                InternalServiceIdentity.ACTION_AUDIENCE_IMPORT_START,
                signatureTimestamp,
                signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal service identity");
        }
        return ApiResponse.ok(importService.startImport(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<ImportDto.StatusResponse> getStatus(@PathVariable String id) {
        return ApiResponse.ok(importService.getStatus(id));
    }

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public PagedResponse<ImportDto.StatusResponse> listImports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ImportDto.StatusResponse> result = importService.listImports(PageRequest.of(page, size));
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<String> cancelImport(@PathVariable String id) {
        importService.cancelImport(id);
        return ApiResponse.ok("Import cancelled");
    }
}
