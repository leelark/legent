package com.legent.audience.controller;

import com.legent.audience.service.AudienceResolutionChunkService;
import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.security.InternalApiTokenValidator;
import com.legent.common.security.InternalServiceIdentity;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/audience-resolution-chunks")
@RequiredArgsConstructor
public class AudienceResolutionChunkController {

    private static final Set<String> ALLOWED_CHUNK_READER_SERVICES = Set.of("campaign-service");

    private final AudienceResolutionChunkService chunkService;

    @Value("${legent.internal.api-token}")
    private String internalApiToken;

    @PostConstruct
    void validateInternalApiToken() {
        InternalApiTokenValidator.requireConfigured("legent.internal.api-token", internalApiToken);
    }

    @GetMapping("/{chunkId}/internal")
    @PreAuthorize("permitAll()")
    public ApiResponse<AudienceResolutionChunkService.ChunkResponse> getInternalChunk(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SERVICE, required = false) String internalService,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP, required = false) String signatureTimestamp,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE, required = false) String signature,
            @RequestHeader(name = AppConstants.HEADER_TENANT_ID) String tenantId,
            @RequestHeader(name = AppConstants.HEADER_WORKSPACE_ID) String workspaceId,
            @PathVariable String chunkId,
            @RequestParam String jobId) {
        if (!InternalServiceIdentity.matches(
                internalApiToken,
                token,
                internalService,
                ALLOWED_CHUNK_READER_SERVICES,
                tenantId,
                workspaceId,
                InternalServiceIdentity.scopedAction(
                        InternalServiceIdentity.ACTION_AUDIENCE_RESOLUTION_CHUNK_READ,
                        jobId,
                        chunkId),
                signatureTimestamp,
                signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal service identity");
        }
        return ApiResponse.ok(chunkService.getChunk(tenantId, workspaceId, jobId, chunkId));
    }
}
