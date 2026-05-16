package com.legent.audience.controller;

import com.legent.audience.dto.SubscriberDto;
import com.legent.audience.service.SubscriberService;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscribers")
@RequiredArgsConstructor
public class SubscriberController {

    private final SubscriberService subscriberService;

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public PagedResponse<SubscriberDto.Response> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        String resolvedSearch = (search != null && !search.isBlank()) ? search : query;
        Page<SubscriberDto.Response> result = subscriberService.search(resolvedSearch, status, PageRequest.of(page, size, sort));
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<SubscriberDto.Response> getById(@PathVariable String id) {
        return ApiResponse.ok(subscriberService.getById(id));
    }

    @GetMapping("/key/{subscriberKey}")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<SubscriberDto.Response> getByKey(@PathVariable String subscriberKey) {
        return ApiResponse.ok(subscriberService.getBySubscriberKey(subscriberKey));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<SubscriberDto.Response> create(@Valid @RequestBody SubscriberDto.CreateRequest request) {
        return ApiResponse.ok(subscriberService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<SubscriberDto.Response> update(@PathVariable String id,
                                                       @Valid @RequestBody SubscriberDto.UpdateRequest request) {
        return ApiResponse.ok(subscriberService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:delete', principal.roles)")
    public void delete(@PathVariable String id) {
        subscriberService.delete(id);
    }

    @PostMapping("/bulk")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<SubscriberDto.BulkUpsertResponse> bulkUpsert(@Valid @RequestBody SubscriberDto.BulkUpsertRequest request) {
        return ApiResponse.ok(subscriberService.bulkUpsert(request));
    }

    @PostMapping("/merge")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<SubscriberDto.Response> merge(@Valid @RequestBody SubscriberDto.MergeRequest request) {
        return ApiResponse.ok(subscriberService.merge(request));
    }

    @PostMapping("/bulk-actions")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<Long> bulkActions(@Valid @RequestBody SubscriberDto.BulkActionRequest request) {
        return ApiResponse.ok(subscriberService.bulkAction(request));
    }

    @PutMapping("/{id}/lifecycle")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<SubscriberDto.Response> updateLifecycle(@PathVariable String id,
                                                               @Valid @RequestBody SubscriberDto.LifecycleUpdateRequest request) {
        return ApiResponse.ok(subscriberService.updateLifecycle(id, request));
    }

    @PutMapping("/{id}/score")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<SubscriberDto.Response> updateScore(@PathVariable String id,
                                                           @Valid @RequestBody SubscriberDto.ScoreUpdateRequest request) {
        return ApiResponse.ok(subscriberService.updateScore(id, request));
    }

    @GetMapping("/{id}/activity")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<SubscriberDto.ActivityTimelineResponse> activity(@PathVariable String id) {
        return ApiResponse.ok(subscriberService.activity(id));
    }

    @GetMapping("/count")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<Long> count() {
        return ApiResponse.ok(subscriberService.count());
    }
}
