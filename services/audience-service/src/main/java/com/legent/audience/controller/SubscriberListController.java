package com.legent.audience.controller;

import com.legent.audience.dto.SubscriberListDto;
import com.legent.audience.service.SubscriberListService;
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
@RequestMapping("/api/v1/lists")
@RequiredArgsConstructor
public class SubscriberListController {

    private final SubscriberListService listService;

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public PagedResponse<SubscriberListDto.Response> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SubscriberListDto.Response> result = listService.list(PageRequest.of(page, size));
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<SubscriberListDto.Response> getById(@PathVariable String id) {
        return ApiResponse.ok(listService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<SubscriberListDto.Response> create(@Valid @RequestBody SubscriberListDto.CreateRequest request) {
        return ApiResponse.ok(listService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<SubscriberListDto.Response> update(@PathVariable String id,
                                                          @Valid @RequestBody SubscriberListDto.UpdateRequest request) {
        return ApiResponse.ok(listService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:delete', principal.roles)")
    public void delete(@PathVariable String id) {
        listService.delete(id);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<Void> addMembers(@PathVariable String id,
                                         @Valid @RequestBody SubscriberListDto.MembershipRequest request) {
        listService.addMembers(id, request.getSubscriberIds());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/members")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<Void> removeMembers(@PathVariable String id,
                                            @Valid @RequestBody SubscriberListDto.MembershipRequest request) {
        listService.removeMembers(id, request.getSubscriberIds());
        return ApiResponse.ok(null);
    }
}
