package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.foundation.dto.PublicContentDto;
import com.legent.foundation.service.PublicContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/public-content")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')")
public class AdminPublicContentController {

    private final PublicContentService publicContentService;

    @GetMapping
    public ApiResponse<List<PublicContentDto.Response>> list() {
        return ApiResponse.ok(publicContentService.listAdminContent());
    }

    @PostMapping
    public ApiResponse<PublicContentDto.Response> create(@Valid @RequestBody PublicContentDto.UpsertRequest request) {
        return ApiResponse.ok(publicContentService.upsert(request, null));
    }

    @PutMapping("/{id}")
    public ApiResponse<PublicContentDto.Response> update(@PathVariable String id,
                                                         @Valid @RequestBody PublicContentDto.UpsertRequest request) {
        return ApiResponse.ok(publicContentService.upsert(request, id));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<PublicContentDto.Response> publish(@PathVariable String id,
                                                          @RequestParam(defaultValue = "true") boolean value) {
        return ApiResponse.ok(publicContentService.publish(id, value));
    }
}

