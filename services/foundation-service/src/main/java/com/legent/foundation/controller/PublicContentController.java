package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.foundation.dto.PublicContactDto;
import com.legent.foundation.dto.PublicContentDto;
import com.legent.foundation.service.PublicContactService;
import com.legent.foundation.service.PublicContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicContentController {

    private final PublicContentService publicContentService;
    private final PublicContactService publicContactService;

    @GetMapping("/content/{page}")
    public ApiResponse<PublicContentDto.Response> contentByPage(
            @PathVariable("page") String page,
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "workspaceId", required = false) String workspaceId) {
        return ApiResponse.ok(publicContentService.getPublishedPage(tenantId, workspaceId, page));
    }

    @GetMapping("/pricing")
    public ApiResponse<PublicContentDto.Response> pricing(
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "workspaceId", required = false) String workspaceId) {
        return ApiResponse.ok(publicContentService.getPublishedPage(tenantId, workspaceId, "pricing"));
    }

    @GetMapping("/blog")
    public ApiResponse<List<PublicContentDto.Response>> blog(
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "workspaceId", required = false) String workspaceId) {
        return ApiResponse.ok(publicContentService.listPublishedBlog(tenantId, workspaceId));
    }

    @GetMapping("/blog/{slug}")
    public ApiResponse<PublicContentDto.Response> blogBySlug(
            @PathVariable("slug") String slug,
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "workspaceId", required = false) String workspaceId) {
        return ApiResponse.ok(publicContentService.getPublishedBlogBySlug(tenantId, workspaceId, slug));
    }

    @PostMapping("/contact")
    public ApiResponse<PublicContactDto.Response> contact(@Valid @RequestBody PublicContactDto.Request request) {
        return ApiResponse.ok(publicContactService.submit(request));
    }
}
