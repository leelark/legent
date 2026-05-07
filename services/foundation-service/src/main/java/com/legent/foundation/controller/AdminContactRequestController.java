package com.legent.foundation.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.foundation.dto.PublicContactDto;
import com.legent.foundation.service.PublicContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/contact-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')")
public class AdminContactRequestController {

    private final PublicContactService publicContactService;

    @GetMapping
    public PagedResponse<PublicContactDto.AdminResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PublicContactDto.AdminResponse> result = publicContactService.listAdmin(
                status,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), AppConstants.MAX_PAGE_SIZE))
        );
        return PagedResponse.from(result);
    }

    @PostMapping("/{id}/status")
    public ApiResponse<PublicContactDto.AdminResponse> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody PublicContactDto.StatusUpdateRequest request) {
        return ApiResponse.ok(publicContactService.updateStatus(id, request));
    }
}
