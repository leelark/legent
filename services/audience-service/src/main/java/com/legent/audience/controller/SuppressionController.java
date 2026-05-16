package com.legent.audience.controller;

import java.util.List;

import com.legent.audience.dto.SuppressionDto;
import com.legent.audience.service.SuppressionService;
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
@RequestMapping("/api/v1/suppressions")
@RequiredArgsConstructor
public class SuppressionController {

    private final SuppressionService suppressionService;

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public PagedResponse<SuppressionDto.Response> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SuppressionDto.Response> result = suppressionService.list(PageRequest.of(page, size));
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<SuppressionDto.Response> create(@Valid @RequestBody SuppressionDto.CreateRequest request) {
        return ApiResponse.ok(suppressionService.create(request));
    }

    @PostMapping("/bulk")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<List<SuppressionDto.Response>> bulkCreate(@Valid @RequestBody SuppressionDto.BulkRequest request) {
        return ApiResponse.ok(suppressionService.bulkCreate(request));
    }

    @GetMapping("/check/{email}")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<SuppressionDto.ComplianceCheck> checkCompliance(@PathVariable String email) {
        return ApiResponse.ok(suppressionService.checkCompliance(email));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:delete', principal.roles)")
    public void delete(@PathVariable String id) {
        suppressionService.delete(id);
    }
}
