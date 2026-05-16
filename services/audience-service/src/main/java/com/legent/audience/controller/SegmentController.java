package com.legent.audience.controller;

import com.legent.audience.dto.SegmentDto;
import com.legent.audience.service.SegmentService;
import com.legent.audience.service.SegmentEvaluationService;
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
@RequestMapping("/api/v1/segments")
@RequiredArgsConstructor
public class SegmentController {

    private final SegmentService segmentService;
    private final SegmentEvaluationService evaluationService;

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public PagedResponse<SegmentDto.Response> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SegmentDto.Response> result = segmentService.list(PageRequest.of(page, size));
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<SegmentDto.Response> getById(@PathVariable String id) {
        return ApiResponse.ok(segmentService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<SegmentDto.Response> create(@Valid @RequestBody SegmentDto.CreateRequest request) {
        return ApiResponse.ok(segmentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<SegmentDto.Response> update(@PathVariable String id,
                                                    @Valid @RequestBody SegmentDto.UpdateRequest request) {
        return ApiResponse.ok(segmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:delete', principal.roles)")
    public void delete(@PathVariable String id) {
        segmentService.delete(id);
    }

    @GetMapping("/{id}/evaluate")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<SegmentDto.CountPreview> evaluate(@PathVariable String id) {
        return ApiResponse.ok(evaluationService.evaluateCount(id));
    }

    @PostMapping("/{id}/recompute")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<String> recompute(@PathVariable @org.springframework.lang.NonNull String id) {
        evaluationService.recompute(
                id,
                com.legent.security.TenantContext.requireTenantId(),
                com.legent.security.TenantContext.requireWorkspaceId());
        return ApiResponse.ok("Segment recomputation started");
    }
}
